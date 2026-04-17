package com.yangyag.todo.gateway;

import com.yangyag.todo.gateway.support.JwtTestTokens;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Gateway 전체 컨텍스트를 띄워 실제 필터 체인이 동작하는지 확인한다.
 * 하위 서비스는 닿지 않는 포트로 라우팅해두었기 때문에, 인증 단계까지만 검증한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiGatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealthIsPublicAndReturnsOk() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() {
        webTestClient.get().uri("/api/todos")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").exists();
    }

    @Test
    void protectedEndpointWithRefreshTokenReturns401() {
        String refreshToken = JwtTestTokens.refreshToken(UUID.randomUUID());

        webTestClient.get().uri("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void protectedEndpointWithInvalidSignatureReturns401() {
        String badToken = JwtTestTokens.accessTokenWithSecret(JwtTestTokens.ALT_SECRET, UUID.randomUUID());

        webTestClient.get().uri("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + badToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void publicLoginPathDoesNotRequireToken() {
        // 닿지 않는 업스트림으로 라우팅되므로 401은 아니어야 한다 (Bad Gateway / 500 등).
        webTestClient
                .mutate()
                .responseTimeout(Duration.ofSeconds(5))
                .build()
                .post().uri("/api/auth/login")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 401) {
                        throw new AssertionError("Public login path should not be rejected with 401");
                    }
                });
    }
}
