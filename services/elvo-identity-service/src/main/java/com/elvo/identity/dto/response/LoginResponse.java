package com.elvo.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UUID sessionId
) {
}
