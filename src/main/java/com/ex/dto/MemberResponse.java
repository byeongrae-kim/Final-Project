package com.ex.dto;

import com.ex.entity.Member;

import java.time.LocalDateTime;
import java.util.List;

public record MemberResponse(
        Long id,
        String username,
        String email,
        String name,
        String farmName,
        String phone,
        String businessNumber,
        LocalDateTime createdAt,
        List<AddressResponse> addresses
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getUsername(),
                member.getEmail(),
                member.getName(),
                member.getFarmName(),
                member.getPhone(),
                member.getBusinessNumber(),
                member.getCreatedAt(),
                member.getAddresses().stream()
                        .map(address -> new AddressResponse(
                                address.getAddressType().name(),
                                address.getRecipientName(),
                                address.getPhone(),
                                address.getPostalCode(),
                                address.getBaseAddress(),
                                address.getDetailAddress(),
                                address.getUnloadingLocation(),
                                address.isDefaultAddress()
                        ))
                        .toList()
        );
    }

    public record AddressResponse(
            String addressType,
            String recipientName,
            String phone,
            String postalCode,
            String baseAddress,
            String detailAddress,
            String unloadingLocation,
            boolean defaultAddress
    ) {
    }
}
