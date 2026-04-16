package com.yangyag.todo.auth.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
    USER("user"),
    ADMIN("admin");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String authority() {
        return "ROLE_" + name();
    }

    @JsonCreator
    public static UserRole fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("User role is required");
        }
        for (UserRole role : values()) {
            if (role.value.equalsIgnoreCase(value) || role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unsupported user role: " + value);
    }
}
