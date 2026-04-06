package com.elvo.wallet.service.model;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalCommand(
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        WithdrawalMode mode,
        String targetNumber,
        String espCode,
        String eacCode,
        String idempotencyKey,
        String reference,
        String stepUpMethod,
        String stepUpToken
) {
}
