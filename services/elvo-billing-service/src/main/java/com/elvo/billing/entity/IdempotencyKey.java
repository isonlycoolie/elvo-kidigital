package com.elvo.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency key tracking entity.
 * Ensures that operations with the same idempotency key are not executed twice.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String key;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType; // e.g., "PAYMENT_CREATION", "PAYMENT_EXECUTION"

    @Column(name = "result_payload", nullable = false, columnDefinition = "jsonb")
    private String resultPayload; // JSON serialization of the result

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IdempotencyKey() {
    }

    public IdempotencyKey(String key, UUID operationId, String operationType, String resultPayload) {
        this.key = key;
        this.operationId = operationId;
        this.operationType = operationType;
        this.resultPayload = resultPayload;
        this.createdAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(String resultPayload) {
        this.resultPayload = resultPayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
