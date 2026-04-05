package com.elvo.wallet.service.model;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositCommand(
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        WalletChannel channel,
        String idempotencyKey,
        String reference,
        boolean agentFloatAvailable,
        String mobileCallbackReference
) {
}
