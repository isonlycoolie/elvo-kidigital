package com.elvo.wallet.entity;

import java.math.BigDecimal;
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

@Entity
@Table(
        name = "delegated_withdrawal_tokens",
        indexes = {
                @Index(name = "idx_dwt_wallet_id", columnList = "wallet_id"),
                @Index(name = "idx_dwt_user_id", columnList = "user_id"),
                @Index(name = "idx_dwt_status", columnList = "status")
        }
)
public class DelegatedWithdrawalToken {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "token_id", nullable = false, updatable = false)
    private UUID tokenId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_reference", nullable = false, unique = true, length = 256)
    private String tokenReference;

    @Column(name = "delegate_reference", nullable = false, length = 128)
    private String delegateReference;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.ISSUED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getTokenId() {
        return tokenId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTokenReference() {
        return tokenReference;
    }

    public void setTokenReference(String tokenReference) {
        this.tokenReference = tokenReference;
    }

    public String getDelegateReference() {
        return delegateReference;
    }

    public void setDelegateReference(String delegateReference) {
        this.delegateReference = delegateReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum Status {
        ISSUED,
        CANCELLED,
        EXPIRED,
        REDEEMED_EXTERNAL_PENDING
    }
}
