package com.elvo.wallet.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ReservationRequestDto {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Expiry date is required")
    private Instant expiryDate;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private UUID reservationId; // Used for release/confirm operations

    public ReservationRequestDto() {
    }

    public ReservationRequestDto(BigDecimal amount, Instant expiryDate, String idempotencyKey) {
        this.amount = amount;
        this.expiryDate = expiryDate;
        this.idempotencyKey = idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }
}
