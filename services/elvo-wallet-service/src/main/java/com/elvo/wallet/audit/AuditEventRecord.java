package com.elvo.wallet.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "wallet_audit_events")
public class AuditEventRecord {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 128, updatable = false)
    private String eventType;

    @Column(name = "request_id", nullable = false, length = 128, updatable = false)
    private String requestId;

    @Column(name = "correlation_id", nullable = false, length = 128, updatable = false)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "payload", nullable = false, length = 4000, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditEventRecord() {
    }

    public AuditEventRecord(String eventType, String requestId, String correlationId, Instant occurredAt, String payload) {
        this.eventType = eventType;
        this.requestId = requestId;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }

    @PreUpdate
    void preventUpdate() {
        throw new UnsupportedOperationException("Audit event records are immutable");
    }
}