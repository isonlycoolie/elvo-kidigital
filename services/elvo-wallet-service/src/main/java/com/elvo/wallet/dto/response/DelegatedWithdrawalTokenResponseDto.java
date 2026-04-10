package com.elvo.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class DelegatedWithdrawalTokenResponseDto {

    private UUID tokenId;
    private UUID walletId;
    private UUID userId;
    private String delegatedToken;
    private String delegateReference;
    private BigDecimal amount;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public DelegatedWithdrawalTokenResponseDto(UUID tokenId,
                                               UUID walletId,
                                               UUID userId,
                                               String delegatedToken,
                                               String delegateReference,
                                               BigDecimal amount,
                                               String status,
                                               Instant expiresAt,
                                               Instant createdAt,
                                               Instant updatedAt) {
        this.tokenId = tokenId;
        this.walletId = walletId;
        this.userId = userId;
        this.delegatedToken = delegatedToken;
        this.delegateReference = delegateReference;
        this.amount = amount;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getTokenId() {
        return tokenId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDelegatedToken() {
        return delegatedToken;
    }

    public String getDelegateReference() {
        return delegateReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
