package com.elvo.wallet.dto.request;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DelegatedWithdrawalTokenIssueRequestDto {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 15, fraction = 4, message = "Amount must have up to 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in the future")
    private Instant expiresAt;

    @NotBlank(message = "Delegate reference is required")
    @Size(max = 128, message = "Delegate reference must be 128 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9._:@+-]+$", message = "Delegate reference may only contain letters, numbers, dot, underscore, colon, at sign, plus, and hyphen")
    private String delegateReference;

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 128, message = "Idempotency key must be between 8 and 128 characters")
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "Idempotency key may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String idempotencyKey;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getDelegateReference() {
        return delegateReference;
    }

    public void setDelegateReference(String delegateReference) {
        this.delegateReference = delegateReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
