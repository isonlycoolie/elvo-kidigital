package com.elvo.identity.dto.response;

import java.time.Instant;

public record VerificationRequiredResponse(
        String status,
        String message,
        String verificationToken,
        Instant verificationTokenExpiresAt
) {
}
