package com.clawhub.user.web;

import com.clawhub.user.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull UserRole role
) {
}
