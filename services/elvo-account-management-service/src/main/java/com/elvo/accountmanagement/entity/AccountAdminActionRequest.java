package com.elvo.accountmanagement.entity;

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
        name = "account_admin_action_requests",
        indexes = {
                @Index(name = "idx_admin_action_requests_account_id", columnList = "account_id"),
                @Index(name = "idx_admin_action_requests_status", columnList = "status")
        }
)
public class AccountAdminActionRequest {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "admin_action_request_id", nullable = false, updatable = false)
    private UUID adminActionRequestId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "restriction_type", length = 64)
    private Account.RestrictionType restrictionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.PENDING_APPROVAL;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approval_note", length = 512)
    private String approvalNote;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public UUID getAdminActionRequestId() {
        return adminActionRequestId;
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

    public Account.RestrictionType getRestrictionType() {
        return restrictionType;
    }

    public void setRestrictionType(Account.RestrictionType restrictionType) {
        this.restrictionType = restrictionType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
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

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovalNote() {
        return approvalNote;
    }

    public void setApprovalNote(String approvalNote) {
        this.approvalNote = approvalNote;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public enum Status {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}
