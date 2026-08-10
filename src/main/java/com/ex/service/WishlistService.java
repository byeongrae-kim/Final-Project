package com.ex.service;

import com.ex.entity.Member;
import com.ex.entity.Product;
import com.ex.entity.WishlistItem;
import com.ex.repository.MemberRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Long> findProductIds(Long memberId) {
        requireMemberId(memberId);
        return wishlistItemRepository.findProductIdsByMemberId(memberId);
    }

    @Transactional
    public void add(Long memberId, Long productId) {
        requireMemberId(memberId);

        if (wishlistItemRepository.existsByMember_IdAndProduct_Id(
                memberId,
                productId
        )) {
            return;
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원정보를 찾을 수 없습니다."));
        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        wishlistItemRepository.save(WishlistItem.builder()
                .member(member)
                .product(product)
                .build());
    }

    @Transactional
    public void remove(Long memberId, Long productId) {
        requireMemberId(memberId);
        wishlistItemRepository.deleteByMember_IdAndProduct_Id(memberId, productId);
    }

    private void requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
    }
}
