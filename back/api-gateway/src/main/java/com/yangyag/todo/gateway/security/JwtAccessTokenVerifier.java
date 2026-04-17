package com.yangyag.todo.gateway.security;

import com.yangyag.todo.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * auth-service {@code JwtService}의 access 토큰 검증 로직을 Gateway에 맞게 재구현한 경량 버전.
 * Gateway는 토큰 발급 책임을 갖지 않으며, 검증 실패 시 {@link JwtException}을 그대로 던진다.
 */
@Component
public class JwtAccessTokenVerifier {

    private static final String CLAIM_LOGIN_ID = "loginId";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final SecretKey signingKey;

    public JwtAccessTokenVerifier(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 서명/만료를 검증하고 access 토큰 여부를 확인한 뒤 최소 정보를 반환한다.
     *
     * @throws JwtException 서명 오류, 만료, 잘못된 token type, subject 파싱 실패 등 모든 검증 실패
     */
    public VerifiedAccessToken verify(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Malformed token: " + ex.getMessage(), ex);
        }

        String tokenTypeClaim = claims.get(CLAIM_TOKEN_TYPE, String.class);
        JwtTokenType tokenType;
        try {
            tokenType = JwtTokenType.fromValue(tokenTypeClaim);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Unsupported token type claim: " + tokenTypeClaim, ex);
        }
        if (tokenType != JwtTokenType.ACCESS) {
            throw new JwtException("Only access tokens are accepted by the gateway");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("Token subject is missing");
        }
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Token subject is not a valid UUID", ex);
        }

        return new VerifiedAccessToken(
                userId,
                claims.get(CLAIM_LOGIN_ID, String.class),
                claims.get(CLAIM_NAME, String.class),
                claims.get(CLAIM_ROLE, String.class));
    }
}
