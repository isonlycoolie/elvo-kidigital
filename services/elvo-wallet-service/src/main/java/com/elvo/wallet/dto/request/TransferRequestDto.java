package com.elvo.wallet.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class TransferRequestDto {

    @NotNull(message = "Target wallet ID is required")
    private UUID targetWalletId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private String reference;

    public TransferRequestDto() {
    }

    public TransferRequestDto(UUID targetWalletId, BigDecimal amount, String idempotencyKey, String reference) {
        this.targetWalletId = targetWalletId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.reference = reference;
    }

    public UUID getTargetWalletId() {
        return targetWalletId;
    }

    public void setTargetWalletId(UUID targetWalletId) {
        this.targetWalletId = targetWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
