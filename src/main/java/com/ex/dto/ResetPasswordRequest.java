package com.ex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank(message = "아이디를 입력해주세요.")
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z0-9_]{4,19}$",
                message = "아이디 형식이 올바르지 않습니다."
        )
        String username,

        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리여야 합니다.")
        String verificationCode,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,64}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함한 8~64자여야 합니다."
        )
        String newPassword
) {
}
