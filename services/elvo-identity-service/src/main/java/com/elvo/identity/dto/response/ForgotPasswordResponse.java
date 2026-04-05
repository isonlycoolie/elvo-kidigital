package com.elvo.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ForgotPasswordResponse(
        UUID userId,
        String resetToken,
        Instant expiresAt
) {
}
