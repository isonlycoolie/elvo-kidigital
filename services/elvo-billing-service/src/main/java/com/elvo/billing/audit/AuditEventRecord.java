package com.elvo.billing.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "billing_audit_events")
public class AuditEventRecord {

    public static final String GENESIS_HASH = "GENESIS";

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

    @Column(name = "previous_hash", nullable = false, length = 64, updatable = false)
    private String previousHash = GENESIS_HASH;

    @Column(name = "record_hash", nullable = false, length = 64, updatable = false)
    private String recordHash;

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

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getPayload() {
        return payload;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getRecordHash() {
        return recordHash;
    }

    public void setRecordHash(String recordHash) {
        this.recordHash = recordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PreUpdate
    @PreRemove
    void preventMutation() {
        throw new UnsupportedOperationException("Billing audit event records are immutable");
    }
}