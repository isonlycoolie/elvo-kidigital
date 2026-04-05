package com.elvo.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SessionInfoResponse(
        UUID sessionId,
        UUID userId,
        UUID deviceId,
        String ipAddress,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        boolean revoked,
        String status
) {
}
