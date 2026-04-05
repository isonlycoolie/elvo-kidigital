package com.elvo.identity.dto.response;

import java.time.Instant;

public record EspGenerateResponse(
        String espCode,
        Instant expiresAt
) {
}
