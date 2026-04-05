package com.elvo.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SessionTokenResponse(
        UUID sessionId,
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
) {
}
