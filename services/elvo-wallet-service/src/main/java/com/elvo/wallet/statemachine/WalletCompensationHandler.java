package com.elvo.wallet.statemachine;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletCompensationHandler {

    private final WalletRetryMechanism retryMechanism;
    private final WalletEventPublisher eventPublisher;

    public WalletCompensationHandler(WalletRetryMechanism retryMechanism,
                                     WalletEventPublisher eventPublisher) {
        this.retryMechanism = retryMechanism;
        this.eventPublisher = eventPublisher;
    }

    public WalletFlowResult compensateFailedBilling(UUID reservationId,
                                                    String idempotencyKey,
                                                    String reason,
                                                    String correlationId) {
        WalletFlowResult rollbackResult = retryMechanism.rollbackWithRetry(reservationId, idempotencyKey);
        if (rollbackResult.success()) {
            eventPublisher.publish("wallet.transaction.reversed", Map.of(
                    "reservationId", reservationId,
                    "reason", reason,
                    "correlationId", correlationId));
            return rollbackResult;
        }

        eventPublisher.publish("wallet.transaction.failed", Map.of(
                "reservationId", reservationId,
                "reason", reason,
                "correlationId", correlationId,
                "rollbackResult", rollbackResult.message()));
        return rollbackResult;
    }
}
