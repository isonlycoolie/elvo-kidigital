package com.elvo.wallet.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ImmutableAuditStorageService {

    private final ImmutableAuditEventStore auditEventStore;
    private final AuditEventSignatureService auditEventSignatureService;

    public ImmutableAuditStorageService(ImmutableAuditEventStore auditEventStore,
                                        AuditEventSignatureService auditEventSignatureService) {
        this.auditEventStore = auditEventStore;
        this.auditEventSignatureService = auditEventSignatureService;
    }

    public void append(String eventType, String requestId, String correlationId, Instant occurredAt, String payload) {
        String signature = auditEventSignatureService.sign(eventType, requestId, correlationId, occurredAt, payload);
        String signedPayload = auditEventSignatureService.attachSignature(payload, signature);
        auditEventStore.append(new AuditEventRecord(eventType, requestId, correlationId, occurredAt, signedPayload));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AUDIT_ADMIN')")
    public List<AuditEventRecord> readRecentEvents(int limit) {
        return auditEventStore.findRecent(limit);
    }
}