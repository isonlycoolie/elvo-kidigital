package com.elvo.wallet.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "challenge_codes")
public class ChallengeCode {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    @Column(name = "max_usage_count", nullable = false)
    private int maxUsageCount = 1;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ChallengeCodeStatus status = ChallengeCodeStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ChallengeCodeStatus {
        ACTIVE,
        LOCKED,
        CONSUMED,
        EXPIRED
    }

    public UUID getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public int getFailedAttemptCount() {
        return failedAttemptCount;
    }

    public void setFailedAttemptCount(int failedAttemptCount) {
        this.failedAttemptCount = failedAttemptCount;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public int getMaxUsageCount() {
        return maxUsageCount;
    }

    public void setMaxUsageCount(int maxUsageCount) {
        this.maxUsageCount = maxUsageCount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public ChallengeCodeStatus getStatus() {
        return status;
    }

    public void setStatus(ChallengeCodeStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
