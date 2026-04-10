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
        name = "account_permission_change_requests",
        indexes = {
                @Index(name = "idx_permission_change_requests_account_id", columnList = "account_id"),
                @Index(name = "idx_permission_change_requests_status", columnList = "status")
        }
)
public class AccountPermissionChangeRequest {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "permission_change_request_id", nullable = false, updatable = false)
    private UUID permissionChangeRequestId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_flag", nullable = false, length = 64)
    private Account.AccountPermissionFlag permissionFlag;

    @Column(name = "previous_enabled", nullable = false)
    private boolean previousEnabled;

    @Column(name = "requested_enabled", nullable = false)
    private boolean requestedEnabled;

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

    public UUID getPermissionChangeRequestId() {
        return permissionChangeRequestId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public Account.AccountPermissionFlag getPermissionFlag() {
        return permissionFlag;
    }

    public void setPermissionFlag(Account.AccountPermissionFlag permissionFlag) {
        this.permissionFlag = permissionFlag;
    }

    public boolean isPreviousEnabled() {
        return previousEnabled;
    }

    public void setPreviousEnabled(boolean previousEnabled) {
        this.previousEnabled = previousEnabled;
    }

    public boolean isRequestedEnabled() {
        return requestedEnabled;
    }

    public void setRequestedEnabled(boolean requestedEnabled) {
        this.requestedEnabled = requestedEnabled;
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
