package com.elvo.accountmanagement.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "accounts",
        indexes = {
                @Index(name = "idx_accounts_user_id", columnList = "user_id"),
                @Index(name = "idx_accounts_ean", columnList = "ean"),
                @Index(name = "idx_accounts_status", columnList = "account_status")
        }
)
public class Account {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "ean", nullable = false, unique = true, length = 64)
    private String ean;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private AccountType accountType = AccountType.WALLET;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private AccountStatus accountStatus = AccountStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 32)
    private KycStatus kycStatus = KycStatus.UNVERIFIED;

    @Column(name = "parent_account_id")
    private UUID parentAccountId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(KycStatus kycStatus) {
        this.kycStatus = kycStatus;
    }

    public UUID getParentAccountId() {
        return parentAccountId;
    }

    public void setParentAccountId(UUID parentAccountId) {
        this.parentAccountId = parentAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public enum AccountType {
        SAVINGS,
        CURRENT,
        WALLET,
        AGENT,
        MERCHANT,
        BUSINESS,
        CHILD,
        EMPLOYEE,
        SHARED,
        SYSTEM
    }

    public enum AccountStatus {
        PENDING,
        ACTIVE,
        FROZEN,
        SUSPENDED,
        RESTRICTED,
        CLOSED,
        ARCHIVED
    }

    public enum KycStatus {
        UNVERIFIED,
        PARTIAL,
        VERIFIED,
        ENHANCED,
        BLOCKED
    }

    public enum AccountPermissionFlag {
        CAN_RECEIVE_MONEY,
        CAN_SEND_MONEY,
        CAN_WITHDRAW,
        CAN_DEPOSIT,
        CAN_USE_DELEGATED_ACCESS,
        CAN_USE_AGENT_WITHDRAWAL,
        CAN_PERFORM_BILL_PAYMENT,
        CAN_CREATE_SUB_ACCOUNTS
    }

    public enum LimitScope {
        DAILY_TRANSFER,
        MONTHLY_TRANSFER,
        WITHDRAWAL,
        DEPOSIT,
        BILL_PAYMENT,
        MAX_SINGLE_TRANSACTION
    }

    public enum RelationshipType {
        PARENT_CHILD,
        EMPLOYER_EMPLOYEE,
        BUSINESS_AGENT,
        SHARED_ACCOUNT,
        GUARDIAN
    }

    public enum RelationshipStatus {
        ACTIVE,
        INACTIVE,
        PENDING,
        TERMINATED
    }

    public enum RestrictionType {
        SEND_BLOCKED,
        RECEIVE_BLOCKED,
        WITHDRAWAL_BLOCKED,
        BILL_PAYMENT_BLOCKED,
        TEMPORARY_LOCK,
        FRAUD_HOLD
    }
}
