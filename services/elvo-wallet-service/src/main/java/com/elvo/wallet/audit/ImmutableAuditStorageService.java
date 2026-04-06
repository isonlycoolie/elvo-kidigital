package com.elvo.wallet.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ImmutableAuditStorageService {

    private final ImmutableAuditEventStore auditEventStore;

    public ImmutableAuditStorageService(ImmutableAuditEventStore auditEventStore) {
        this.auditEventStore = auditEventStore;
    }

    public void append(String eventType, String requestId, String correlationId, Instant occurredAt, String payload) {
        auditEventStore.append(new AuditEventRecord(eventType, requestId, correlationId, occurredAt, payload));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AUDIT_ADMIN')")
    public List<AuditEventRecord> readRecentEvents(int limit) {
        return auditEventStore.findRecent(limit);
    }
}