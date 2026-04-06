package com.elvo.wallet.dto.response;

import java.time.Instant;

public record AuditEventResponseDto(
        String eventType,
        String requestId,
        String correlationId,
        Instant occurredAt,
        String payload,
        String previousHash,
        String recordHash,
        Instant createdAt
) {
}