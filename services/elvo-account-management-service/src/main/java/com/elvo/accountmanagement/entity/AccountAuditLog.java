package com.elvo.accountmanagement.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "account_audit_log",
        indexes = {
                @Index(name = "idx_account_audit_account_id", columnList = "account_id"),
                @Index(name = "idx_account_audit_request_id", columnList = "request_id")
        }
)
public class AccountAuditLog {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "audit_log_id", nullable = false, updatable = false)
    private UUID auditLogId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "source_service", length = 128)
    private String sourceService;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "source_user_agent", length = 255)
    private String sourceUserAgent;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getAuditLogId() {
        return auditLogId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getSourceUserAgent() {
        return sourceUserAgent;
    }

    public void setSourceUserAgent(String sourceUserAgent) {
        this.sourceUserAgent = sourceUserAgent;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
