package com.yangyag.todo.gateway.security;

public enum JwtTokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    JwtTokenType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static JwtTokenType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Token type claim is missing");
        }
        for (JwtTokenType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported token type: " + value);
    }
}
