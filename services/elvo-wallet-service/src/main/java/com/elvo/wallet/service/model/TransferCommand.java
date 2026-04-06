package com.elvo.wallet.service.model;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(
        UUID sourceWalletId,
        UUID targetWalletId,
        UUID userId,
        BigDecimal amount,
        String idempotencyKey,
        String reference,
        String stepUpMethod,
        String stepUpToken,
        String transactionChallengeToken
) {
}
