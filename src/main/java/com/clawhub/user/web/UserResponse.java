package com.clawhub.user.web;

import com.clawhub.user.domain.AppUser;
import com.clawhub.user.domain.UserRole;
import com.clawhub.user.domain.UserStatus;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
