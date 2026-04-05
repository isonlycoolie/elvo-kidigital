package com.elvo.wallet.service.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationCommand(
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        Instant expiryDate,
        String idempotencyKey,
        String reference
) {
}
