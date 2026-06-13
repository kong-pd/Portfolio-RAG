package com.portfolio.rag.auth;

import com.portfolio.rag.auth.dto.LoginResponse;
import com.portfolio.rag.auth.entity.RefreshToken;
import com.portfolio.rag.auth.entity.User;
import com.portfolio.rag.common.ApiException;
import com.portfolio.rag.config.AppProperties;
import com.portfolio.rag.repository.RefreshTokenRepository;
import com.portfolio.rag.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    @Transactional
    public Long register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "该邮箱已被注册");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .build();
        return userRepository.save(user).getId();
    }

    @Transactional
    public LoginResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "邮箱或密码错误"));

        String accessToken = jwtService.generateAccessToken(user.getId());
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .token(UUID.randomUUID())
                .expiresAt(Instant.now().plus(properties.getJwt().getRefreshTokenTtl()))
                .build();
        refreshTokenRepository.save(refreshToken);
        return new LoginResponse(accessToken, refreshToken.getToken().toString());
    }

    /** No rotation (D-04): returns a fresh access token, refresh token stays valid. */
    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(parseToken(refreshTokenValue))
                .filter(t -> !t.isRevoked())
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(this::refreshTokenInvalid);
        String accessToken = jwtService.generateAccessToken(refreshToken.getUserId());
        return new LoginResponse(accessToken, refreshTokenValue);
    }

    @Transactional
    public void logout(Long userId, String refreshTokenValue) {
        UUID token;
        try {
            token = UUID.fromString(refreshTokenValue);
        } catch (IllegalArgumentException e) {
            return; // nothing to revoke; logout is idempotent
        }
        refreshTokenRepository.findByToken(token)
                .filter(t -> t.getUserId().equals(userId))
                .ifPresent(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                });
    }

    private UUID parseToken(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw refreshTokenInvalid();
        }
    }

    private ApiException refreshTokenInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "刷新令牌无效或已过期");
    }
}
