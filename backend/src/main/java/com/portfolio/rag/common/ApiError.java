package com.portfolio.rag.common;

import java.time.Instant;

/**
 * Standard error body: {@code {code, message, timestamp}}.
 * Instant serializes as ISO-8601 (WRITE_DATES_AS_TIMESTAMPS is off by default in Boot).
 */
public record ApiError(String code, String message, Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now());
    }
}
