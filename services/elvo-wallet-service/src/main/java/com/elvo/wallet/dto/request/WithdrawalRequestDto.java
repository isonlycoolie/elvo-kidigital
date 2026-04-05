package com.elvo.wallet.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class WithdrawalRequestDto {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Mode is required")
    private String mode; // REGISTERED_NUMBER, OTHER_NUMBER, DEVICE_FREE

    private String targetNumber;

    private String espCode;

    private String eacCode;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private String reference;

    public WithdrawalRequestDto() {
    }

    public WithdrawalRequestDto(BigDecimal amount, String mode, String targetNumber, String espCode,
                               String eacCode, String idempotencyKey, String reference) {
        this.amount = amount;
        this.mode = mode;
        this.targetNumber = targetNumber;
        this.espCode = espCode;
        this.eacCode = eacCode;
        this.idempotencyKey = idempotencyKey;
        this.reference = reference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTargetNumber() {
        return targetNumber;
    }

    public void setTargetNumber(String targetNumber) {
        this.targetNumber = targetNumber;
    }

    public String getEspCode() {
        return espCode;
    }

    public void setEspCode(String espCode) {
        this.espCode = espCode;
    }

    public String getEacCode() {
        return eacCode;
    }

    public void setEacCode(String eacCode) {
        this.eacCode = eacCode;
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
