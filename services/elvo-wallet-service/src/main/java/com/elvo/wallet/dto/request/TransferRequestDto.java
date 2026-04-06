package com.elvo.wallet.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TransferRequestDto {

    @NotNull(message = "Target wallet ID is required")
    private UUID targetWalletId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "2000.0000", message = "Transfer amount must not exceed 2000.0000")
    @Digits(integer = 15, fraction = 4, message = "Amount must have up to 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 128, message = "Idempotency key must be between 8 and 128 characters")
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "Idempotency key may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String idempotencyKey;

    @Size(max = 128, message = "Reference must be 128 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "Reference may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String reference;

    @Pattern(regexp = "PASSWORD|PIN|BIOMETRIC|MFA", message = "Step-up method must be PASSWORD, PIN, BIOMETRIC, or MFA")
    private String stepUpMethod;

    @Size(max = 256, message = "Step-up token must be 256 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "Step-up token may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String stepUpToken;

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

    public String getStepUpMethod() {
        return stepUpMethod;
    }

    public void setStepUpMethod(String stepUpMethod) {
        this.stepUpMethod = stepUpMethod;
    }

    public String getStepUpToken() {
        return stepUpToken;
    }

    public void setStepUpToken(String stepUpToken) {
        this.stepUpToken = stepUpToken;
    }
}
