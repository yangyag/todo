package com.yangyag.todo.gateway.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * auth-service의 JwtService가 발급하는 토큰과 동일한 스펙으로 서명한 테스트용 토큰 발급 헬퍼.
 */
public final class JwtTestTokens {

    public static final String SECRET = "test-secret-test-secret-test-secret-test-secret-2026";
    public static final String ALT_SECRET = "alt-secret-alt-secret-alt-secret-alt-secret-1234567890";

    private JwtTestTokens() {
    }

    public static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static String accessToken(UUID userId) {
        return signedAccessToken(SECRET, userId, "tester", "Tester", "user", Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    public static String accessTokenWithSecret(String secret, UUID userId) {
        return signedAccessToken(secret, userId, "tester", "Tester", "user", Instant.now(), Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    public static String expiredAccessToken(UUID userId) {
        Instant issuedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant expiresAt = Instant.now().minus(5, ChronoUnit.MINUTES);
        return signedAccessToken(SECRET, userId, "tester", "Tester", "user", issuedAt, expiresAt);
    }

    public static String refreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("loginId", "tester")
                .claim("name", "Tester")
                .claim("role", "user")
                .claim("tokenType", "refresh")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)))
                .signWith(key(SECRET), Jwts.SIG.HS256)
                .compact();
    }

    public static String accessTokenWithoutTokenType(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("loginId", "tester")
                .claim("name", "Tester")
                .claim("role", "user")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .signWith(key(SECRET), Jwts.SIG.HS256)
                .compact();
    }

    public static String accessTokenWithInvalidSubject() {
        return Jwts.builder()
                .subject("not-a-uuid")
                .id(UUID.randomUUID().toString())
                .claim("loginId", "tester")
                .claim("name", "Tester")
                .claim("role", "user")
                .claim("tokenType", "access")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .signWith(key(SECRET), Jwts.SIG.HS256)
                .compact();
    }

    private static String signedAccessToken(
            String secret,
            UUID userId,
            String loginId,
            String name,
            String role,
            Instant issuedAt,
            Instant expiresAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("loginId", loginId)
                .claim("name", name)
                .claim("role", role)
                .claim("tokenType", "access")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key(secret), Jwts.SIG.HS256)
                .compact();
    }
}
