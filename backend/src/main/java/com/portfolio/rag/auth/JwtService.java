package com.portfolio.rag.auth;

import com.portfolio.rag.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates HS256 access tokens. The subject claim carries the userId.
 * Validation never throws; callers receive an empty Optional / error code instead.
 */
@Service
public class JwtService {

    public static final String ERROR_TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(resolveKeyBytes(properties.getJwt().getSecret()));
        this.accessTokenTtl = properties.getJwt().getAccessTokenTtl();
    }

    /**
     * Prefers a Base64-encoded secret; falls back to raw UTF-8 bytes. Secrets
     * shorter than 256 bits are stretched with SHA-256 so HS256 stays valid.
     */
    private static byte[] resolveKeyBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured (env JWT_SECRET)");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException e) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            try {
                bytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
        return bytes;
    }

    public String generateAccessToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** Returns the userId if the token is valid, empty otherwise. Never throws. */
    public Optional<Long> validate(String token) {
        return Optional.ofNullable(validateDetailed(token).userId());
    }

    /** Like {@link #validate} but distinguishes expired tokens from invalid ones. */
    public ValidationResult validateDetailed(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ValidationResult(Long.parseLong(claims.getSubject()), null);
        } catch (ExpiredJwtException e) {
            return new ValidationResult(null, ERROR_TOKEN_EXPIRED);
        } catch (Exception e) {
            return new ValidationResult(null, ERROR_UNAUTHORIZED);
        }
    }

    public record ValidationResult(Long userId, String errorCode) {
    }
}
