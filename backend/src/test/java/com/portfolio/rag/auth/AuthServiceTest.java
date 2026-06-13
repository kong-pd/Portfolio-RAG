package com.portfolio.rag.auth;

import com.portfolio.rag.auth.dto.LoginResponse;
import com.portfolio.rag.auth.entity.RefreshToken;
import com.portfolio.rag.auth.entity.User;
import com.portfolio.rag.common.ApiException;
import com.portfolio.rag.config.AppProperties;
import com.portfolio.rag.repository.RefreshTokenRepository;
import com.portfolio.rag.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("irrelevant-for-these-tests");
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, jwtService, properties);
    }

    // ------------------------------------------------------------------
    // register
    // ------------------------------------------------------------------

    @Test
    void register_newEmail_returnsUserId() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(1L);
            return user;
        });

        Long userId = authService.register("new@example.com", "password123");

        assertThat(userId).isEqualTo(1L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("dup@example.com", "password123"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
                });
        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    @Test
    void login_validCredentials_returnsTokens() {
        User user = User.builder().id(3L).email("u@example.com").passwordHash("hash").build();
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(3L)).thenReturn("access-token");

        LoginResponse response = authService.login("u@example.com", "password123");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(UUID.fromString(response.refreshToken())).isNotNull();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(3L);
        assertThat(saved.getToken().toString()).isEqualTo(response.refreshToken());
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = User.builder().id(3L).email("u@example.com").passwordHash("hash").build();
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("u@example.com", "wrong"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("BAD_CREDENTIALS");
                });
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@example.com", "password123"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("BAD_CREDENTIALS");
                });
    }

    // ------------------------------------------------------------------
    // refresh
    // ------------------------------------------------------------------

    @Test
    void refresh_validToken_returnsNewAccessToken() {
        UUID token = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .userId(3L)
                .token(token)
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(3L)).thenReturn("new-access-token");

        LoginResponse response = authService.refresh(token.toString());

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        // no rotation: same refresh token comes back
        assertThat(response.refreshToken()).isEqualTo(token.toString());
    }

    @Test
    void refresh_revokedToken_throwsUnauthorized() {
        UUID token = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .userId(3L)
                .token(token)
                .revoked(true)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(token.toString()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("REFRESH_TOKEN_INVALID");
                });
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        UUID token = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .userId(3L)
                .token(token)
                .revoked(false)
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(token.toString()))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void refresh_invalidUUID_throwsUnauthorized() {
        assertThatThrownBy(() -> authService.refresh("not-a-uuid"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("REFRESH_TOKEN_INVALID");
                });
        verify(refreshTokenRepository, never()).findByToken(any());
    }

    // ------------------------------------------------------------------
    // logout
    // ------------------------------------------------------------------

    @Test
    void logout_validToken_revokesIt() {
        UUID token = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .userId(3L)
                .token(token)
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(stored));

        authService.logout(3L, token.toString());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isRevoked()).isTrue();
    }

    @Test
    void logout_tokenNotOwned_doesNothing() {
        UUID token = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .userId(99L) // belongs to another user
                .token(token)
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByToken(token)).thenReturn(Optional.of(stored));

        authService.logout(3L, token.toString());

        verify(refreshTokenRepository, never()).save(any());
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void logout_invalidUUID_isIdempotentNoOp() {
        authService.logout(3L, "garbage");

        verify(refreshTokenRepository, never()).findByToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }
}
