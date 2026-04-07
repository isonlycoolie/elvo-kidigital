package com.elvo.wallet.service.orchestration;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.elvo.wallet.service.WalletTransactionService;
import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletTransactionOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(WalletTransactionOrchestrator.class);

    private final WalletTransactionService walletTransactionService;

    public WalletTransactionOrchestrator(WalletTransactionService walletTransactionService) {
        this.walletTransactionService = walletTransactionService;
    }

    @RabbitListener(queues = "${elvo.messaging.billing.completed-queue:billing.transaction.completed.queue}")
    public void onBillingCompleted(Map<String, Object> event) {
        String reservationId = payloadValue(event, "reservationId");
        if (reservationId == null) {
            reservationId = payloadValue(event, "transactionId");
        }
        String idempotencyKey = payloadValue(event, "idempotencyKey");
        if (idempotencyKey == null) {
            idempotencyKey = payloadValue(event, "transactionId");
        }
        if (reservationId == null || idempotencyKey == null) {
            LOG.warn("wallet_orchestrator_skip_commit reason=missing_reservation_or_idempotency");
            return;
        }

        WalletFlowResult result = walletTransactionService.commitFunds(UUID.fromString(reservationId), idempotencyKey);
        LOG.info("wallet_orchestrator_commit reservationId={} success={} message={}", reservationId, result.success(), result.message());
    }

    @RabbitListener(queues = "${elvo.messaging.billing.reversed-queue:billing.transaction.reversed.queue}")
    public void onBillingReversed(Map<String, Object> event) {
        String reservationId = payloadValue(event, "reservationId");
        if (reservationId == null) {
            reservationId = payloadValue(event, "transactionId");
        }
        String idempotencyKey = payloadValue(event, "idempotencyKey");
        if (idempotencyKey == null) {
            idempotencyKey = payloadValue(event, "transactionId");
        }
        if (reservationId == null || idempotencyKey == null) {
            LOG.warn("wallet_orchestrator_skip_rollback reason=missing_reservation_or_idempotency");
            return;
        }

        WalletFlowResult result = walletTransactionService.rollbackFunds(UUID.fromString(reservationId), idempotencyKey);
        LOG.info("wallet_orchestrator_rollback reservationId={} success={} message={}", reservationId, result.success(), result.message());
    }

    @SuppressWarnings("unchecked")
    private String payloadValue(Map<String, Object> event, String key) {
        if (event == null) {
            return null;
        }
        Object payload = event.get("payload");
        if (!(payload instanceof Map<?, ?> mapPayload)) {
            return null;
        }
        Object value = ((Map<String, Object>) mapPayload).get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
