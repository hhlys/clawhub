package com.clawhub.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may contain only letters, numbers, dot, underscore, and hyphen")
        String username,
        @NotBlank
        @Size(min = 8, max = 128)
        String password
) {
}
