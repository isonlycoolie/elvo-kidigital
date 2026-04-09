package com.elvo.wallet.statemachine;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletEventSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(WalletEventSubscriber.class);

    private final WalletEventPublisher eventPublisher;
    private final WalletStateTransitionHandlers stateTransitionHandlers;

    public WalletEventSubscriber(WalletEventPublisher eventPublisher,
                                 WalletStateTransitionHandlers stateTransitionHandlers) {
        this.eventPublisher = eventPublisher;
        this.stateTransitionHandlers = stateTransitionHandlers;
    }

    @RabbitListener(queues = "${elvo.messaging.billing.requested-queue:billing.transaction.requested.queue}")
    @Transactional
    public void onBillingTransactionRequested(Map<String, Object> event) {
        Map<String, Object> payload = payloadOf(event);
        String walletId = stringValue(payload.get("walletId"));
        String userId = stringValue(payload.get("userId"));
        BigDecimal amount = decimalValue(payload.get("amount"));
        String billingReference = stringValue(payload.get("transactionId"));
        String idempotencyKey = stringValue(payload.get("idempotencyKey"));
        String correlationId = resolveCorrelationId(event);

        if (walletId == null || amount == null || userId == null || idempotencyKey == null) {
            LOG.warn("wallet_event_subscriber_invalid_payload walletId={} amount={} correlationId={}",
                    walletId,
                    amount,
                    correlationId);
            Map<String, Object> failedPayload = new HashMap<>();
            failedPayload.put("paymentId", billingReference == null ? UUID.randomUUID().toString() : billingReference);
            failedPayload.put("transactionId", billingReference == null ? UUID.randomUUID().toString() : billingReference);
            failedPayload.put("idempotencyKey", idempotencyKey == null ? "missing-idempotency-key" : idempotencyKey);
            failedPayload.put("reason", "missing_wallet_or_amount");
            publishFailedEvent(failedPayload, correlationId);
            return;
        }

            WalletFlowResult reserveResult = stateTransitionHandlers.reserveFunds(
                UUID.fromString(walletId),
                UUID.fromString(userId),
                amount,
                idempotencyKey,
                billingReference);

            if (!reserveResult.success()) {
                Map<String, Object> failedPayload = new HashMap<>();
                String safeReference = billingReference == null ? UUID.randomUUID().toString() : billingReference;
                failedPayload.put("paymentId", safeReference);
                failedPayload.put("transactionId", safeReference);
                failedPayload.put("idempotencyKey", idempotencyKey == null ? safeReference : idempotencyKey);
                failedPayload.put("reason", reserveResult.message() == null ? "reservation_failed" : reserveResult.message());
                publishFailedEvent(failedPayload, correlationId);
                return;
            }

        eventPublisher.publish("wallet.transaction.reserved", Map.of(
                "walletId", walletId,
                "amount", amount,
                "transactionId", billingReference,
                "correlationId", correlationId));
    }

    private void publishFailedEvent(Map<String, Object> failedPayload, String correlationId) {
        try {
            eventPublisher.publish("wallet.transaction.failed", failedPayload);
        } catch (RuntimeException ex) {
            LOG.error("wallet_event_subscriber_publish_failed correlationId={}", correlationId, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadOf(Map<String, Object> event) {
        Object payload = event == null ? null : event.get("payload");
        if (payload instanceof Map<?, ?> mapPayload) {
            return (Map<String, Object>) mapPayload;
        }
        return Map.of();
    }

    private String resolveCorrelationId(Map<String, Object> event) {
        return event == null ? null : stringValue(event.get("correlationId"));
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
