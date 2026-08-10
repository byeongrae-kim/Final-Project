package com.ex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FindUsernameRequest(
        @NotBlank(message = "성명을 입력해주세요.")
        @Size(max = 40, message = "성명은 40자 이하여야 합니다.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 120, message = "이메일은 120자 이하여야 합니다.")
        String email
) {
}
