package com.elvo.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransactionResponseDto {

    private UUID id;
    private UUID walletId;
    private String type;
    private BigDecimal amount;
    private String status;
    private String reference;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public TransactionResponseDto() {
    }

    public TransactionResponseDto(UUID id, UUID walletId, String type, BigDecimal amount, String status,
                                 String reference, String description, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.status = status;
        this.reference = reference;
        this.description = description;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
