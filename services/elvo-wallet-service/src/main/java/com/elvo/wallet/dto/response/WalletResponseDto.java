package com.elvo.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class WalletResponseDto {

    private UUID id;
    private UUID userId;
    private BigDecimal balance;
    private BigDecimal reservedBalance;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public WalletResponseDto() {
    }

    public WalletResponseDto(UUID id, UUID userId, BigDecimal balance, BigDecimal reservedBalance,
                           String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.balance = balance;
        this.reservedBalance = reservedBalance;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public void setReservedBalance(BigDecimal reservedBalance) {
        this.reservedBalance = reservedBalance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
