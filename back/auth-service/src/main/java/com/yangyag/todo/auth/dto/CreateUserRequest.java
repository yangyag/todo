package com.yangyag.todo.auth.dto;

import com.yangyag.todo.auth.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String loginId,
        @NotBlank @Size(min = 8, max = 255) String password,
        @NotBlank @Size(max = 100) String name,
        UserRole role,
        Boolean isActive) {

    @NotNull
    public UserRole resolvedRole() {
        return role == null ? UserRole.USER : role;
    }

    public boolean resolvedActive() {
        return isActive == null || isActive;
    }
}
