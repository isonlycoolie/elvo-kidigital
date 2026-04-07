package com.elvo.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import com.elvo.wallet.service.model.WalletFlowResult;

public interface WalletTransactionService {

    WalletFlowResult reserveFunds(UUID walletId,
                                  UUID userId,
                                  BigDecimal amount,
                                  String idempotencyKey,
                                  String reference);

    WalletFlowResult commitFunds(UUID reservationId, String idempotencyKey);

    WalletFlowResult rollbackFunds(UUID reservationId, String idempotencyKey);
}
