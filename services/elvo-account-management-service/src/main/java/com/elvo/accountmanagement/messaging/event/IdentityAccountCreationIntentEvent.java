package com.elvo.accountmanagement.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record IdentityAccountCreationIntentEvent(
        UUID eventId,
        String eventVersion,
        Instant occurredAt,
        String correlationId,
        UUID userId,
        String ean,
        String email,
        String phone,
        String displayName,
        boolean mfaEnabled,
        String sourceService,
        String sourceIp,
        String sourceUserAgent) {
}
