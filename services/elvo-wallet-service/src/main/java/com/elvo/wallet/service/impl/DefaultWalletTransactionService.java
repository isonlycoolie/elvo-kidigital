package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.WalletTransactionService;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.statemachine.WalletRetryMechanism;

@Service
public class DefaultWalletTransactionService implements WalletTransactionService {

    private final WalletRetryMechanism walletRetryMechanism;

    public DefaultWalletTransactionService(WalletRetryMechanism walletRetryMechanism) {
        this.walletRetryMechanism = walletRetryMechanism;
    }

    @Override
    public WalletFlowResult reserveFunds(UUID walletId,
                                         UUID userId,
                                         BigDecimal amount,
                                         String idempotencyKey,
                                         String reference) {
        return walletRetryMechanism.reserveWithRetry(walletId, userId, amount, idempotencyKey, reference);
    }

    @Override
    public WalletFlowResult commitFunds(UUID reservationId, String idempotencyKey) {
        return walletRetryMechanism.commitWithRetry(reservationId, idempotencyKey);
    }

    @Override
    public WalletFlowResult rollbackFunds(UUID reservationId, String idempotencyKey) {
        return walletRetryMechanism.rollbackWithRetry(reservationId, idempotencyKey);
    }
}
