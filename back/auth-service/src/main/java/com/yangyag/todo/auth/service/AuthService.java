package com.yangyag.todo.auth.service;

import com.yangyag.todo.auth.dto.LoginRequest;
import com.yangyag.todo.auth.dto.LogoutRequest;
import com.yangyag.todo.auth.dto.RefreshRequest;
import com.yangyag.todo.auth.dto.TokenResponse;
import com.yangyag.todo.auth.dto.UserResponse;
import com.yangyag.todo.auth.entity.RefreshToken;
import com.yangyag.todo.auth.entity.User;
import com.yangyag.todo.auth.exception.UnauthorizedException;
import com.yangyag.todo.auth.repository.RefreshTokenRepository;
import com.yangyag.todo.auth.repository.UserRepository;
import com.yangyag.todo.auth.security.JwtClaims;
import com.yangyag.todo.auth.security.JwtService;
import com.yangyag.todo.auth.security.JwtTokenType;
import com.yangyag.todo.auth.security.AuthenticatedUser;
import io.jsonwebtoken.JwtException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenHashService tokenHashService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHashService = tokenHashService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new UnauthorizedException("Invalid login credentials"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Inactive user");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid login credentials");
        }

        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        JwtClaims claims = parseRefreshClaims(request.refreshToken());
        String tokenHash = tokenHashService.hash(request.refreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));

        if (claims.tokenId() == null
                || !storedToken.getId().equals(claims.tokenId())
                || !storedToken.getUser().getId().equals(claims.userId())) {
            throw new UnauthorizedException("Refresh token is invalid");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (storedToken.getExpiresAt() != null && storedToken.getExpiresAt().isBefore(now)) {
            refreshTokenRepository.delete(storedToken);
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = storedToken.getUser();
        if (!user.isActive()) {
            refreshTokenRepository.delete(storedToken);
            throw new UnauthorizedException("Inactive user");
        }

        refreshTokenRepository.delete(storedToken);
        return issueTokenPair(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.deleteByTokenHash(tokenHashService.hash(request.refreshToken()));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(AuthenticatedUser currentUser) {
        User user = userRepository.findById(currentUser.id())
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
        return UserResponse.from(user);
    }

    private JwtClaims parseRefreshClaims(String refreshToken) {
        try {
            return jwtService.parse(refreshToken, JwtTokenType.REFRESH);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Refresh token is invalid");
        }
    }

    private TokenResponse issueTokenPair(User user) {
        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(user);
        UUID refreshTokenId = UUID.randomUUID();
        JwtService.IssuedToken refreshTokenValue = jwtService.issueRefreshToken(user, refreshTokenId);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(refreshTokenId);
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHashService.hash(refreshTokenValue.value()));
        refreshToken.setExpiresAt(refreshTokenValue.expiresAt());
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(accessToken.value(), refreshTokenValue.value());
    }
}
