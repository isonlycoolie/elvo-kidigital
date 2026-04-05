package com.elvo.wallet.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DepositRequestDto {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "10000.0000", message = "Deposit amount must not exceed 10000.0000")
    @Digits(integer = 15, fraction = 4, message = "Amount must have up to 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Channel is required")
    @Pattern(regexp = "AGENT|MOBILE|INTERNAL", message = "Channel must be AGENT, MOBILE, or INTERNAL")
    private String channel; // AGENT, MOBILE, INTERNAL

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 128, message = "Idempotency key must be between 8 and 128 characters")
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "Idempotency key may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String idempotencyKey;

    @Size(max = 128, message = "Reference must be 128 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "Reference may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String reference;

    @Size(max = 128, message = "Mobile callback reference must be 128 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "Mobile callback reference may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String mobileCallbackReference;

    public DepositRequestDto() {
    }

    public DepositRequestDto(BigDecimal amount, String channel, String idempotencyKey, String reference,
                            String mobileCallbackReference) {
        this.amount = amount;
        this.channel = channel;
        this.idempotencyKey = idempotencyKey;
        this.reference = reference;
        this.mobileCallbackReference = mobileCallbackReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
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

    public String getMobileCallbackReference() {
        return mobileCallbackReference;
    }

    public void setMobileCallbackReference(String mobileCallbackReference) {
        this.mobileCallbackReference = mobileCallbackReference;
    }
}
