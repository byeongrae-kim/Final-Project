package com.ex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(
        @NotBlank @Size(max = 40) String name,
        @NotBlank @Size(max = 100) String farmName,
        @NotBlank @Pattern(regexp = "^[0-9-]{10,13}$") String phone,
        @Pattern(regexp = "^$|^[0-9]{3}-?[0-9]{2}-?[0-9]{5}$")
        @Size(max = 20) String businessNumber,
        @NotBlank @Size(max = 200) String homeAddress,
        @Size(max = 200) String homeDetailAddress,
        @NotBlank @Size(max = 200) String farmAddress,
        @Size(max = 200) String unloadingLocation
) {
}
