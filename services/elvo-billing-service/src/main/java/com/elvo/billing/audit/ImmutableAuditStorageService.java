package com.elvo.billing.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ImmutableAuditStorageService {

    private final ImmutableAuditEventStore auditEventStore;

    public ImmutableAuditStorageService(ImmutableAuditEventStore auditEventStore) {
        this.auditEventStore = auditEventStore;
    }

    public void append(String eventType, String requestId, String correlationId, Instant occurredAt, String payload) {
        AuditEventRecord record = new AuditEventRecord(
                defaultIfBlank(eventType, "billing.audit.unknown"),
                defaultIfBlank(requestId, UUID.randomUUID().toString()),
                defaultIfBlank(correlationId, UUID.randomUUID().toString()),
                occurredAt == null ? Instant.now() : occurredAt,
                payload == null ? "{}" : payload);
        auditEventStore.append(record);
    }

    public List<AuditEventRecord> readRecentEvents(int limit) {
        return auditEventStore.findRecent(limit);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}