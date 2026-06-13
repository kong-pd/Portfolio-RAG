package com.portfolio.rag.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {
}
