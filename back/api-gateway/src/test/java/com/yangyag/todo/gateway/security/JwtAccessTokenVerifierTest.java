package com.yangyag.todo.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangyag.todo.gateway.config.JwtProperties;
import com.yangyag.todo.gateway.support.JwtTestTokens;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtAccessTokenVerifierTest {

    private JwtAccessTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(JwtTestTokens.SECRET);
        verifier = new JwtAccessTokenVerifier(props);
    }

    @Test
    void verifyReturnsUserInformationFromValidAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = JwtTestTokens.accessToken(userId);

        VerifiedAccessToken verified = verifier.verify(token);

        assertThat(verified.userId()).isEqualTo(userId);
        assertThat(verified.loginId()).isEqualTo("tester");
        assertThat(verified.name()).isEqualTo("Tester");
        assertThat(verified.role()).isEqualTo("user");
    }

    @Test
    void verifyRejectsRefreshToken() {
        String token = JwtTestTokens.refreshToken(UUID.randomUUID());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRejectsExpiredToken() {
        String token = JwtTestTokens.expiredAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRejectsTokenSignedWithDifferentSecret() {
        String token = JwtTestTokens.accessTokenWithSecret(JwtTestTokens.ALT_SECRET, UUID.randomUUID());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRejectsTokenWithMissingTokenTypeClaim() {
        String token = JwtTestTokens.accessTokenWithoutTokenType(UUID.randomUUID());

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRejectsTokenWithNonUuidSubject() {
        String token = JwtTestTokens.accessTokenWithInvalidSubject();

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void verifyRejectsGarbageInput() {
        assertThatThrownBy(() -> verifier.verify("not-a-token")).isInstanceOf(JwtException.class);
    }
}
