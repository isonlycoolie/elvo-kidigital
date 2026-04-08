package com.elvo.wallet.messaging.producer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.elvo.wallet.audit.ImmutableAuditStorageService;
import com.elvo.wallet.messaging.outbox.WalletOutboxDispatcher;
import com.elvo.wallet.messaging.outbox.WalletOutboxService;
import com.elvo.wallet.monitoring.SentryExceptionReporter;
import com.elvo.wallet.monitoring.WalletMetricsRecorder;
import com.elvo.wallet.security.InternalServiceMessageAuthenticator;

@Component
public class WalletEventPublisher {

    private static final String SOURCE_SERVICE = "elvo-wallet-service";

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String version;
    private final SentryExceptionReporter sentryExceptionReporter;
    private final WalletMetricsRecorder metricsRecorder;
    private final ImmutableAuditStorageService immutableAuditStorageService;
    private final WalletOutboxService outboxService;
    private final WalletOutboxDispatcher outboxDispatcher;

    public WalletEventPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${elvo.messaging.wallet.exchange:elvo.wallet.exchange}") String exchange,
                                @Value("${elvo.messaging.wallet.version:v1}") String version) {
        this(rabbitTemplate, exchange, version, null, null, null, null, null);
    }

    @Autowired
    public WalletEventPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${elvo.messaging.wallet.exchange:elvo.wallet.exchange}") String exchange,
                                @Value("${elvo.messaging.wallet.version:v1}") String version,
                                @Nullable SentryExceptionReporter sentryExceptionReporter,
                                @Nullable WalletMetricsRecorder metricsRecorder,
                                @Nullable ImmutableAuditStorageService immutableAuditStorageService,
                                @Nullable WalletOutboxService outboxService,
                                @Nullable WalletOutboxDispatcher outboxDispatcher) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.version = version;
        this.sentryExceptionReporter = sentryExceptionReporter;
        this.metricsRecorder = metricsRecorder;
        this.immutableAuditStorageService = immutableAuditStorageService;
        this.outboxService = outboxService;
        this.outboxDispatcher = outboxDispatcher;
    }

    public void publish(String eventType, Map<String, Object> payload) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("version", version);
        event.put("requestId", resolveRequestId());
        event.put("correlationId", resolveCorrelationId());
        Instant occurredAt = Instant.now();
        event.put("occurredAt", occurredAt.toString());
        event.put("messageId", UUID.randomUUID().toString());
        event.put("nonce", UUID.randomUUID().toString());
        event.put("expiresAt", occurredAt.plus(5, ChronoUnit.MINUTES).toString());
        event.put("payload", payload == null ? Map.of() : payload);
        Map<String, Object> signedEvent = InternalServiceMessageAuthenticator.signEvent(SOURCE_SERVICE, event);

        if (immutableAuditStorageService != null) {
            try {
                immutableAuditStorageService.append(
                        String.valueOf(signedEvent.get("eventType")),
                        String.valueOf(signedEvent.get("requestId")),
                        String.valueOf(signedEvent.get("correlationId")),
                        Instant.parse(String.valueOf(signedEvent.get("occurredAt"))),
                        String.valueOf(signedEvent.get("payload"))
                );
            } catch (RuntimeException ex) {
                // Preserve core transaction flow even if audit persistence is temporarily unavailable.
            }
        }

        if (outboxService != null && outboxDispatcher != null) {
            UUID outboxId = outboxService.enqueue(
                    eventType,
                    eventType,
                    signedEvent,
                    String.valueOf(signedEvent.get("requestId")),
                    String.valueOf(signedEvent.get("correlationId")),
                    Instant.parse(String.valueOf(signedEvent.get("occurredAt"))));
            dispatchAfterCommit(outboxId);
            return;
        }

        try {
            rabbitTemplate.convertAndSend(exchange, eventType, signedEvent);
            if (metricsRecorder != null) {
                metricsRecorder.recordEventPublish(eventType, true);
            }
        } catch (RuntimeException ex) {
            if (metricsRecorder != null) {
                metricsRecorder.recordEventPublish(eventType, false);
            }
            if (sentryExceptionReporter != null) {
                sentryExceptionReporter.captureCriticalException(
                        ex,
                        null,
                        Map.of(
                                "eventType", String.valueOf(eventType),
                                "exchange", exchange,
                                "version", version));
            }
            throw ex;
        }
    }

    private void dispatchAfterCommit(UUID outboxId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxDispatcher.dispatchById(outboxId);
                }
            });
            return;
        }
        outboxDispatcher.dispatchById(outboxId);
    }

    private String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    private String resolveCorrelationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}
