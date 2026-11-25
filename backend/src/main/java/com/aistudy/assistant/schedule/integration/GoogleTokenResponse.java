package com.aistudy.assistant.schedule.integration;

public record GoogleTokenResponse(
        String accessToken,
        String refreshToken,
        String scope,
        Long expiresIn
) {
}
