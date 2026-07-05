package com.elvo.wallet.client;

import java.math.BigDecimal;
import java.util.UUID;

public interface MobileMoneyDisbursementClient {

    DisbursementResult disburse(UUID userId, String msisdn, BigDecimal amount, String reference);

    record DisbursementResult(boolean success, String externalReference, String message) {
    }
}
