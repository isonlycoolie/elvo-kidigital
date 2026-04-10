package com.elvo.accountmanagement.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "account_limit_change_requests",
        indexes = {
                @Index(name = "idx_limit_change_requests_account_id", columnList = "account_id"),
                @Index(name = "idx_limit_change_requests_status", columnList = "status"),
                @Index(name = "idx_limit_change_requests_activation_at", columnList = "activation_at")
        }
)
public class AccountLimitChangeRequest {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "limit_change_request_id", nullable = false, updatable = false)
    private UUID limitChangeRequestId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_scope", nullable = false, length = 64)
    private Account.LimitScope limitScope;

    @Column(name = "previous_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal previousAmount;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "activation_at", nullable = false)
    private Instant activationAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.PENDING;

    @Column(name = "activated_at")
    private Instant activatedAt;

    public UUID getLimitChangeRequestId() {
        return limitChangeRequestId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public Account.LimitScope getLimitScope() {
        return limitScope;
    }

    public void setLimitScope(Account.LimitScope limitScope) {
        this.limitScope = limitScope;
    }

    public BigDecimal getPreviousAmount() {
        return previousAmount;
    }

    public void setPreviousAmount(BigDecimal previousAmount) {
        this.previousAmount = previousAmount;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getActivationAt() {
        return activationAt;
    }

    public void setActivationAt(Instant activationAt) {
        this.activationAt = activationAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public enum Status {
        PENDING,
        ACTIVATED,
        REJECTED,
        CANCELLED
    }
}
