package com.elvo.accountmanagement.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record AccountAuditTrailEvent(
        UUID eventId,
        String eventVersion,
        UUID auditLogId,
        UUID accountId,
        String actionType,
        String description,
        String requestId,
        String correlationId,
        String sourceService,
        String sourceIp,
        String sourceUserAgent,
        String createdBy,
        Instant occurredAt) {
}
