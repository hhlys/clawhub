package com.clawhub.user.web;

import com.clawhub.user.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may contain only letters, numbers, dot, underscore, and hyphen")
        String username,
        @NotBlank
        @Size(min = 8, max = 128)
        String password,
        @NotNull
        UserRole role
) {
}
