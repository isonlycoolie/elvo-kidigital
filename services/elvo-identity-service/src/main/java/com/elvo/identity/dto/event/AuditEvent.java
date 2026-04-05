package com.elvo.identity.dto.event;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID eventId,
        String eventVersion,
        Instant occurredAt,
        String correlationId,
        String actionType,
        String description,
        String sourceType,
        String sourceIp,
        String sourceUserAgent,
        UUID userId,
        UUID sessionId,
        UUID deviceId) {
}
