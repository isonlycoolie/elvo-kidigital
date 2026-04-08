package com.elvo.billing.entity;

import com.elvo.billing.security.BillingFieldEncryptionService;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "response_payload", nullable = false, columnDefinition = "text")
    private String responsePayload = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResponsePayload() {
        return BillingFieldEncryptionService.decrypt(responsePayload);
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = BillingFieldEncryptionService.encrypt(responsePayload);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}
