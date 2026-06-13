package com.portfolio.rag.auth;

import com.portfolio.rag.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-for-junit-only-256-bit-minimum-length!!";

    private static AppProperties makeProps(String secret, Duration ttl) {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(secret);
        props.getJwt().setAccessTokenTtl(ttl);
        return props;
    }

    @Test
    void generateAccessToken_returnsValidJwt() {
        JwtService service = new JwtService(makeProps(TEST_SECRET, Duration.ofMinutes(15)));

        String token = service.generateAccessToken(42L);

        assertThat(token).isNotBlank();
        Optional<Long> userId = service.validate(token);
        assertThat(userId).contains(42L);
    }

    @Test
    void validate_expiredToken_returnsEmpty() {
        // negative TTL produces an already-expired token, no sleeping needed
        JwtService service = new JwtService(makeProps(TEST_SECRET, Duration.ofSeconds(-10)));

        String token = service.generateAccessToken(42L);

        assertThat(service.validate(token)).isEmpty();
    }

    @Test
    void validate_tampered_returnsEmpty() {
        JwtService service = new JwtService(makeProps(TEST_SECRET, Duration.ofMinutes(15)));

        String token = service.generateAccessToken(42L);
        String tampered = token + "xx";

        assertThat(service.validate(tampered)).isEmpty();
    }

    @Test
    void validate_wrongKey_returnsEmpty() {
        JwtService issuer = new JwtService(makeProps(TEST_SECRET, Duration.ofMinutes(15)));
        JwtService verifier = new JwtService(
                makeProps("a-completely-different-secret-also-long-enough-for-hs256", Duration.ofMinutes(15)));

        String token = issuer.generateAccessToken(42L);

        assertThat(verifier.validate(token)).isEmpty();
    }

    @Test
    void validateDetailed_expired_hasTokenExpiredCode() {
        JwtService service = new JwtService(makeProps(TEST_SECRET, Duration.ofSeconds(-10)));

        String token = service.generateAccessToken(42L);
        JwtService.ValidationResult result = service.validateDetailed(token);

        assertThat(result.userId()).isNull();
        assertThat(result.errorCode()).isEqualTo(JwtService.ERROR_TOKEN_EXPIRED);
    }

    @Test
    void validateDetailed_invalid_hasUnauthorizedCode() {
        JwtService service = new JwtService(makeProps(TEST_SECRET, Duration.ofMinutes(15)));

        JwtService.ValidationResult result = service.validateDetailed("not-a-jwt-at-all");

        assertThat(result.userId()).isNull();
        assertThat(result.errorCode()).isEqualTo(JwtService.ERROR_UNAUTHORIZED);
    }

    @Test
    void validateDetailed_validToken_hasNoErrorCode() {
        JwtService service = new JwtService(makeProps(TEST_SECRET, Duration.ofMinutes(15)));

        JwtService.ValidationResult result = service.validateDetailed(service.generateAccessToken(7L));

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.errorCode()).isNull();
    }
}
