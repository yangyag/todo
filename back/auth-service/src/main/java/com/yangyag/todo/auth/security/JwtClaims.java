package com.yangyag.todo.auth.security;

import com.yangyag.todo.auth.entity.UserRole;
import java.time.OffsetDateTime;
import java.util.UUID;

public record JwtClaims(
        UUID userId,
        UUID tokenId,
        String loginId,
        String name,
        UserRole role,
        JwtTokenType tokenType,
        OffsetDateTime expiresAt) {
}
