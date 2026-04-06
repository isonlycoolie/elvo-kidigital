package com.elvo.wallet.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class InternalReservationActionRequestDto {

    @NotNull(message = "Reservation ID is required")
    private UUID reservationId;

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 8, max = 128, message = "Idempotency key must be between 8 and 128 characters")
    @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "Idempotency key may only contain letters, numbers, dot, underscore, colon, and hyphen")
    private String idempotencyKey;

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}