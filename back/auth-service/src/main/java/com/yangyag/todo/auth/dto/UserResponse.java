package com.yangyag.todo.auth.dto;

import com.yangyag.todo.auth.entity.User;
import com.yangyag.todo.auth.entity.UserRole;
import com.yangyag.todo.auth.security.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String loginId,
        String name,
        UserRole role,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public static UserResponse from(AuthenticatedUser user) {
        return new UserResponse(
                user.id(),
                user.loginId(),
                user.name(),
                user.role(),
                user.active(),
                user.createdAt(),
                user.updatedAt());
    }
}
