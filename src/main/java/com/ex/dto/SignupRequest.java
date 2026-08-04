package com.ex.dto;

import com.ex.entity.AddressType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z0-9_]{4,19}$",
                message = "아이디는 영문으로 시작하는 5~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다."
        )
        String username,

        @NotBlank
        @Email(message = "올바른 이메일 형식으로 입력해주세요.")
        @Size(max = 120)
        String email,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,64}$",
                message = "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다."
        )
        String password,

        @NotBlank @Size(max = 40) String name,
        @NotBlank @Size(max = 100) String farmName,

        @NotBlank
        @Pattern(
                regexp = "^[0-9-]{10,13}$",
                message = "휴대전화 번호 형식이 올바르지 않습니다."
        )
        String phone,

        @Pattern(
                regexp = "^$|^[0-9]{3}-?[0-9]{2}-?[0-9]{5}$",
                message = "사업자번호는 123-45-67890 형식으로 입력해주세요."
        )
        @Size(max = 20)
        String businessNumber,

        @NotNull @Valid AddressRequest homeAddress,
        @NotNull @Valid AddressRequest farmAddress
) {
    public record AddressRequest(
            @NotNull AddressType addressType,
            @NotBlank @Size(max = 40) String recipientName,
            @NotBlank @Pattern(regexp = "^[0-9-]{10,13}$") String phone,
            @Size(max = 10) String postalCode,
            @NotBlank @Size(max = 200) String baseAddress,
            @Size(max = 200) String detailAddress,
            @Size(max = 200) String unloadingLocation,
            boolean defaultAddress
    ) {
    }
}
