package com.yangyag.todo.auth.security;

import com.yangyag.todo.auth.config.JwtProperties;
import com.yangyag.todo.auth.entity.User;
import com.yangyag.todo.auth.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String CLAIM_LOGIN_ID = "loginId";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issueAccessToken(User user) {
        return issueToken(user, JwtTokenType.ACCESS, UUID.randomUUID(), jwtProperties.getAccessTokenExpiration());
    }

    public IssuedToken issueRefreshToken(User user, UUID tokenId) {
        return issueToken(user, JwtTokenType.REFRESH, tokenId, jwtProperties.getRefreshTokenExpiration());
    }

    public JwtClaims parse(String token, JwtTokenType expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        JwtTokenType actualType = JwtTokenType.fromValue(claims.get(CLAIM_TOKEN_TYPE, String.class));
        if (actualType != expectedType) {
            throw new JwtException("Unexpected token type");
        }

        UUID tokenId = claims.getId() == null ? null : UUID.fromString(claims.getId());
        return new JwtClaims(
                UUID.fromString(claims.getSubject()),
                tokenId,
                claims.get(CLAIM_LOGIN_ID, String.class),
                claims.get(CLAIM_NAME, String.class),
                UserRole.fromValue(claims.get(CLAIM_ROLE, String.class)),
                actualType,
                OffsetDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneOffset.UTC));
    }

    private IssuedToken issueToken(User user, JwtTokenType tokenType, UUID tokenId, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        String token = Jwts.builder()
                .subject(user.getId().toString())
                .id(tokenId.toString())
                .claim(CLAIM_LOGIN_ID, user.getLoginId())
                .claim(CLAIM_NAME, user.getName())
                .claim(CLAIM_ROLE, user.getRole().getValue())
                .claim(CLAIM_TOKEN_TYPE, tokenType.getValue())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public record IssuedToken(String value, OffsetDateTime expiresAt) {
    }
}
