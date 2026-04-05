package com.elvo.identity.dto.response;

import java.time.Instant;

public record FastLoginChallengeResponse(
        String pin,
        Instant expiresAt,
        boolean mfaFallbackRequired
) {
}
