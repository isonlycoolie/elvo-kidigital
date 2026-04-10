package com.elvo.identity.dto.response;

import java.time.Instant;

public record TotpEnrollmentResponse(
    String secret,
    String otpauthUrl,
    Instant expiresAt
) {
}