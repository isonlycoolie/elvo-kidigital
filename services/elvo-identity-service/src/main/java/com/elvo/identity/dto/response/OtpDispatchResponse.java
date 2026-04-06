package com.elvo.identity.dto.response;

import java.time.Instant;

public record OtpDispatchResponse(
        String requestId,
        String destinationMask,
        Instant expiresAt
) {
}
