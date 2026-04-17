package com.yangyag.todo.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangyag.todo.gateway.config.JwtProperties;
import com.yangyag.todo.gateway.security.JwtAccessTokenVerifier;
import com.yangyag.todo.gateway.support.JwtTestTokens;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtAuthFilterTest {

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(JwtTestTokens.SECRET);
        JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(props);
        filter = new JwtAuthFilter(verifier);
    }

    @Test
    void publicLoginPathSkipsVerificationAndStripsClientProvidedUserHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/auth/login")
                .header(JwtAuthFilter.USER_ID_HEADER, "00000000-0000-0000-0000-000000000000")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNotNull();
        assertThat(chain.captured().getRequest().getHeaders().getFirst(JwtAuthFilter.USER_ID_HEADER)).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void publicRefreshPathSkipsVerification() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/refresh").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void actuatorPathSkipsVerification() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void corsPreflightIsAlwaysAllowed() {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(HttpMethod.OPTIONS, "/api/todos")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void missingAuthorizationHeaderReturnsUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(readResponseBody(exchange)).contains("\"error\":\"UNAUTHORIZED\"");
    }

    @Test
    void malformedAuthorizationHeaderReturnsUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Token abc.def.ghi")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void emptyBearerTokenReturnsUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer  ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validAccessTokenInjectsUserIdHeader() {
        UUID userId = UUID.randomUUID();
        String token = JwtTestTokens.accessToken(userId);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNotNull();
        HttpHeaders headers = chain.captured().getRequest().getHeaders();
        assertThat(headers.getFirst(JwtAuthFilter.USER_ID_HEADER)).isEqualTo(userId.toString());
        assertThat(headers.getFirst(JwtAuthFilter.USER_LOGIN_ID_HEADER)).isEqualTo("tester");
        assertThat(headers.getFirst(JwtAuthFilter.USER_ROLE_HEADER)).isEqualTo("user");
    }

    @Test
    void clientProvidedUserIdHeaderIsOverwrittenByVerifiedSubject() {
        UUID userId = UUID.randomUUID();
        UUID impersonated = UUID.randomUUID();
        String token = JwtTestTokens.accessToken(userId);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(JwtAuthFilter.USER_ID_HEADER, impersonated.toString())
                .header(JwtAuthFilter.USER_LOGIN_ID_HEADER, "impersonator")
                .header(JwtAuthFilter.USER_ROLE_HEADER, "admin")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = chain.captured().getRequest().getHeaders();
        List<String> userIdHeaders = headers.get(JwtAuthFilter.USER_ID_HEADER);
        assertThat(userIdHeaders).containsExactly(userId.toString());
        assertThat(headers.getFirst(JwtAuthFilter.USER_LOGIN_ID_HEADER)).isEqualTo("tester");
        assertThat(headers.getFirst(JwtAuthFilter.USER_ROLE_HEADER)).isEqualTo("user");
    }

    @Test
    void refreshTokenInAuthorizationHeaderIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = JwtTestTokens.refreshToken(userId);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredAccessTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = JwtTestTokens.expiredAccessToken(userId);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = JwtTestTokens.accessTokenWithSecret(JwtTestTokens.ALT_SECRET, userId);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenWithoutTokenTypeClaimIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = JwtTestTokens.accessTokenWithoutTokenType(userId);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenWithInvalidSubjectIsRejected() {
        String token = JwtTestTokens.accessTokenWithInvalidSubject();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/todos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chain.captured()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filterOrderIsBeforeDefaultGatewayFilters() {
        assertThat(filter.getOrder()).isLessThan(0);
    }

    private String readResponseBody(MockServerWebExchange exchange) {
        Flux<DataBuffer> body = exchange.getResponse().getBody();
        StringBuilder sb = new StringBuilder();
        body.toIterable().forEach(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            sb.append(new String(bytes, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    /**
     * {@link GatewayFilterChain}의 테스트 더블. 마지막으로 받은 exchange를 보관한다.
     */
    private static final class CapturingChain implements GatewayFilterChain {
        private final AtomicReference<ServerWebExchange> ref = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            ref.set(exchange);
            return Mono.empty();
        }

        ServerWebExchange captured() {
            return ref.get();
        }
    }
}
