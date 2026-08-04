package com.ex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z0-9_]{4,19}$",
                message = "아이디 형식이 올바르지 않습니다."
        )
        String username,
        @NotBlank String password
) {
}
