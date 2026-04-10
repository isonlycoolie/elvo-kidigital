package com.elvo.wallet.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DeviceFreeWithdrawalRequestDto {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000.0000", message = "Withdrawal amount must not exceed 1000.0000")
    @Digits(integer = 15, fraction = 4, message = "Amount must have up to 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Target number is required")
    @Size(max = 32, message = "Target number must be 32 characters or fewer")
    @Pattern(regexp = "^[+0-9]{7,32}$", message = "Target number must contain only digits and an optional leading plus")
    private String targetNumber;

    @NotBlank(message = "ESP code is required")
    @Size(min = 4, max = 64, message = "ESP code must be between 4 and 64 characters")
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "ESP code may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String espCode;

    @NotBlank(message = "EAC code is required")
    @Size(min = 4, max = 64, message = "EAC code must be between 4 and 64 characters")
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "EAC code may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String eacCode;

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

    @Size(max = 2048, message = "Transaction challenge token must be 2048 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Transaction challenge token may only contain URL-safe JWT characters")
    private String transactionChallengeToken;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public String getTransactionChallengeToken() {
        return transactionChallengeToken;
    }

    public void setTransactionChallengeToken(String transactionChallengeToken) {
        this.transactionChallengeToken = transactionChallengeToken;
    }
}
