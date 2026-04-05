package com.elvo.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ReservationResponseDto {

    private UUID id;
    private UUID walletId;
    private BigDecimal amount;
    private String status;
    private Instant expiryDate;
    private Instant createdAt;
    private Instant updatedAt;

    public ReservationResponseDto() {
    }

    public ReservationResponseDto(UUID id, UUID walletId, BigDecimal amount, String status,
                                 Instant expiryDate, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.walletId = walletId;
        this.amount = amount;
        this.status = status;
        this.expiryDate = expiryDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
