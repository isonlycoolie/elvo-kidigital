package com.elvo.wallet.statemachine;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.elvo.wallet.messaging.producer.WalletEventPublisher;

@Component
public class WalletEventSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(WalletEventSubscriber.class);

    private final WalletEventPublisher eventPublisher;

    public WalletEventSubscriber(WalletEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @RabbitListener(queues = "${elvo.messaging.billing.requested-queue:billing.transaction.requested.queue}")
    public void onBillingTransactionRequested(Map<String, Object> event) {
        Map<String, Object> payload = payloadOf(event);
        String walletId = stringValue(payload.get("walletId"));
        BigDecimal amount = decimalValue(payload.get("amount"));
        String billingReference = stringValue(payload.get("transactionId"));
        String correlationId = resolveCorrelationId(event);

        if (walletId == null || amount == null) {
            LOG.warn("wallet_event_subscriber_invalid_payload walletId={} amount={} correlationId={}",
                    walletId,
                    amount,
                    correlationId);
            Map<String, Object> failedPayload = new HashMap<>();
            failedPayload.put("walletId", walletId);
            failedPayload.put("amount", amount);
            failedPayload.put("reason", "missing_wallet_or_amount");
            failedPayload.put("transactionId", billingReference);
            failedPayload.put("correlationId", correlationId);
            eventPublisher.publish("wallet.transaction.failed", failedPayload);
            return;
        }

        eventPublisher.publish("wallet.transaction.reserved", Map.of(
                "walletId", walletId,
                "amount", amount,
                "transactionId", billingReference,
                "correlationId", correlationId));
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
