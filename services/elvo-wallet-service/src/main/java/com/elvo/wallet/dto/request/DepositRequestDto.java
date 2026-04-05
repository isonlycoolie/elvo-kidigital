package com.elvo.wallet.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class DepositRequestDto {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Channel is required")
    private String channel; // AGENT, MOBILE, INTERNAL

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private String reference;

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
