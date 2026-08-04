package com.ex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "아이디를 입력해주세요.")
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z0-9_]{4,19}$",
                message = "아이디 형식이 올바르지 않습니다."
        )
        String username,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 120, message = "이메일은 120자 이하여야 합니다.")
        String email,

        @NotBlank(message = "휴대전화를 입력해주세요.")
        @Size(max = 20, message = "휴대전화는 20자 이하여야 합니다.")
        String phone,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,64}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함한 8~64자여야 합니다."
        )
        String newPassword
) {
}
