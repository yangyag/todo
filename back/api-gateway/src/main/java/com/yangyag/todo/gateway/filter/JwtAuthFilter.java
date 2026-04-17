package com.yangyag.todo.gateway.filter;

import com.yangyag.todo.gateway.security.JwtAccessTokenVerifier;
import com.yangyag.todo.gateway.security.VerifiedAccessToken;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Access token을 검증하고 {@code X-User-Id} 헤더를 주입하는 Global filter.
 *
 * <ul>
 *   <li>Public path(로그인/토큰 갱신/actuator)는 검증을 스킵한다.</li>
 *   <li>그 외 경로는 {@code Authorization: Bearer ...} 형식을 강제한다.</li>
 *   <li>클라이언트가 직접 보낸 {@code X-User-Id} 헤더는 항상 제거하고, 검증이 성공한 경우에만 재주입한다.</li>
 * </ul>
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_LOGIN_ID_HEADER = "X-User-LoginId";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<PublicEndpoint> PUBLIC_ENDPOINTS = List.of(
            new PublicEndpoint(HttpMethod.POST, "/api/auth/login"),
            new PublicEndpoint(HttpMethod.POST, "/api/auth/refresh"),
            new PublicEndpoint(null, "/actuator/**"));

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final JwtAccessTokenVerifier verifier;

    public JwtAuthFilter(JwtAccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // CORS preflight는 항상 통과시킨다.
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        if (isPublicPath(request)) {
            return chain.filter(sanitize(exchange));
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return unauthorized(exchange, "Authorization header is missing or malformed");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return unauthorized(exchange, "Bearer token is empty");
        }

        VerifiedAccessToken verified;
        try {
            verified = verifier.verify(token);
        } catch (JwtException ex) {
            return unauthorized(exchange, ex.getMessage() == null ? "Invalid access token" : ex.getMessage());
        }

        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_LOGIN_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    headers.set(USER_ID_HEADER, verified.userId().toString());
                    if (verified.loginId() != null) {
                        headers.set(USER_LOGIN_ID_HEADER, verified.loginId());
                    }
                    if (verified.role() != null) {
                        headers.set(USER_ROLE_HEADER, verified.role());
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // Spring Security의 기본 필터와 충돌하지 않게 충분히 앞쪽에 둔다.
        return -100;
    }

    private boolean isPublicPath(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        for (PublicEndpoint endpoint : PUBLIC_ENDPOINTS) {
            if (endpoint.matches(pathMatcher, method, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Public path 요청에서도 클라이언트가 심은 유저 식별 헤더는 반드시 제거한다.
     */
    private ServerWebExchange sanitize(ServerWebExchange exchange) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_LOGIN_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String safeMessage = message == null ? "Unauthorized" : message.replace("\"", "'");
        String body = "{\"error\":\"UNAUTHORIZED\",\"message\":\"" + safeMessage + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private record PublicEndpoint(HttpMethod method, String pattern) {
        boolean matches(AntPathMatcher matcher, HttpMethod requestMethod, String path) {
            if (method != null && !method.equals(requestMethod)) {
                return false;
            }
            return matcher.match(pattern, path);
        }
    }
}
