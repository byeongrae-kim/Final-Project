package com.ex.repository;

import com.ex.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @Query("""
            select item.product.id
            from WishlistItem item
            where item.member.id = :memberId
            order by item.id desc
            """)
    List<Long> findProductIdsByMemberId(@Param("memberId") Long memberId);

    boolean existsByMember_IdAndProduct_Id(Long memberId, Long productId);

    long deleteByMember_IdAndProduct_Id(Long memberId, Long productId);
}
