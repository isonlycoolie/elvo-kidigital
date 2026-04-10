package com.elvo.accountmanagement.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AccountLifecyclePolicyEvent(
        UUID eventId,
        String eventVersion,
        String category,
        String eventType,
        UUID accountId,
        String accountStatus,
        String kycStatus,
        String reason,
        String requestId,
        String correlationId,
        String sourceService,
        String sourceIp,
        String sourceUserAgent,
        String actor,
        Instant occurredAt) {
}
