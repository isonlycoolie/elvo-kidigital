package com.elvo.identity.dto.response;

import java.time.Instant;

public record EacGenerateResponse(
        String eacCode,
        Instant expiresAt
) {
}
