package com.yangyag.todo.gateway.security;

import java.util.UUID;

/**
 * 검증을 통과한 access 토큰에서 추출한 최소 정보.
 * Gateway는 하위 서비스에 UUID만 전달하면 되지만, 향후 헤더 확장을 위해 claim 일부를 함께 보관한다.
 */
public record VerifiedAccessToken(
        UUID userId,
        String loginId,
        String name,
        String role) {
}
