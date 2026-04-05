package com.elvo.wallet.service.model;

import java.time.Instant;
import java.util.UUID;

public record EtcCommand(
        UUID walletId,
        UUID userId,
        String code,
        Instant expiresAt,
        String idempotencyKey
) {
}
