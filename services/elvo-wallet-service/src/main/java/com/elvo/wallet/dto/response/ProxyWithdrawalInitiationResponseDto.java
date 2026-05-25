package com.elvo.wallet.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProxyWithdrawalInitiationResponseDto(
        boolean success,
        String message,
        UUID walletId,
        UUID reservationId,
        String transactionId,
        String withdrawalCode,
        Instant expiresAt,
        String eventType
) {
}
