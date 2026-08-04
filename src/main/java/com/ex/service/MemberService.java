package com.ex.service;

import com.ex.dto.LoginRequest;
import com.ex.dto.MemberResponse;
import com.ex.dto.MemberUpdateRequest;
import com.ex.dto.FindUsernameRequest;
import com.ex.dto.FindUsernameResponse;
import com.ex.dto.ResetPasswordRequest;
import com.ex.dto.SignupRequest;
import com.ex.entity.AddressType;
import com.ex.entity.DeliveryAddress;
import com.ex.entity.Member;
import com.ex.entity.MemberRole;
import com.ex.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String username = normalizeUsername(request.username());
        String email = request.email().trim().toLowerCase();

        if (memberRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        Member member = Member.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .name(request.name().trim())
                .farmName(request.farmName().trim())
                .phone(request.phone().trim())
                .businessNumber(blankToNull(request.businessNumber()))
                .role(MemberRole.CUSTOMER)
                .active(true)
                .build();

        addAddress(member, request.homeAddress());
        addAddress(member, request.farmAddress());

        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional(readOnly = true)
    public MemberResponse login(LoginRequest request) {
        Member member = memberRepository.findByUsernameIgnoreCase(normalizeUsername(request.username()))
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }
        return MemberResponse.from(member);
    }

    @Transactional(readOnly = true)
    public FindUsernameResponse findUsername(FindUsernameRequest request) {
        String name = request.name().trim();
        String email = normalizeEmail(request.email());

        Member member = memberRepository
                .findByNameIgnoreCaseAndEmailIgnoreCase(name, email)
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "입력한 정보와 일치하는 회원을 찾을 수 없습니다."
                ));

        return new FindUsernameResponse(
                member.getUsername(),
                "가입한 아이디를 찾았습니다."
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());

        Member member = memberRepository
                .findByUsernameIgnoreCaseAndEmailIgnoreCase(
                        username,
                        email
                )
                .filter(Member::isActive)
                .filter(foundMember -> normalizePhone(foundMember.getPhone()).equals(phone))
                .orElseThrow(() -> new IllegalArgumentException(
                        "입력한 회원 정보가 일치하지 않습니다."
                ));

        if (passwordEncoder.matches(request.newPassword(), member.getPassword())) {
            throw new IllegalArgumentException(
                    "현재 비밀번호와 다른 새 비밀번호를 입력해주세요."
            );
        }

        member.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        String normalized = normalizeUsername(username);
        if (!normalized.matches("^[a-z][a-z0-9_]{4,19}$")) {
            throw new IllegalArgumentException(
                    "아이디는 영문으로 시작하는 5~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다."
            );
        }
        return !memberRepository.existsByUsernameIgnoreCase(normalized);
    }

    @Transactional(readOnly = true)
    public MemberResponse findById(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException("로그인 회원 정보를 찾을 수 없습니다."));
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse update(Long memberId, MemberUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new IllegalArgumentException("로그인 회원 정보를 찾을 수 없습니다."));
        member.setName(request.name());
        member.setFarmName(request.farmName());
        member.setPhone(request.phone());
        member.setBusinessNumber(request.businessNumber());

        updateAddress(member, AddressType.HOME, request.homeAddress(), request.homeDetailAddress(), "");
        updateAddress(member, AddressType.FARM, request.farmAddress(), "", request.unloadingLocation());
        return MemberResponse.from(member);
    }

    private void updateAddress(
            Member member,
            AddressType type,
            String baseAddress,
            String detailAddress,
            String unloadingLocation
    ) {
        DeliveryAddress address = member.getAddresses().stream()
                .filter(item -> item.getAddressType() == type)
                .findFirst()
                .orElseGet(() -> {
                    DeliveryAddress created = DeliveryAddress.builder()
                            .member(member)
                            .addressType(type)
                            .defaultAddress(type == AddressType.HOME)
                            .build();
                    member.getAddresses().add(created);
                    return created;
                });
        address.setRecipientName(member.getName());
        address.setPhone(member.getPhone());
        address.setBaseAddress(baseAddress);
        address.setDetailAddress(detailAddress);
        address.setUnloadingLocation(unloadingLocation);
    }

    private void addAddress(Member member, SignupRequest.AddressRequest request) {
        DeliveryAddress address = DeliveryAddress.builder()
                .member(member)
                .addressType(request.addressType())
                .recipientName(request.recipientName())
                .phone(request.phone())
                .postalCode(request.postalCode())
                .baseAddress(request.baseAddress())
                .detailAddress(request.detailAddress())
                .unloadingLocation(request.unloadingLocation())
                .defaultAddress(request.defaultAddress())
                .build();
        member.getAddresses().add(address);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    // 하이픈 포함 여부와 관계없이 같은 휴대전화 번호로 비교합니다.
    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
