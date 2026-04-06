package com.elvo.wallet.messaging.producer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.elvo.wallet.audit.ImmutableAuditStorageService;
import com.elvo.wallet.monitoring.SentryExceptionReporter;
import com.elvo.wallet.monitoring.WalletMetricsRecorder;

@Component
public class WalletEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String version;
    private final SentryExceptionReporter sentryExceptionReporter;
    private final WalletMetricsRecorder metricsRecorder;
    private final ImmutableAuditStorageService immutableAuditStorageService;

    public WalletEventPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${elvo.messaging.wallet.exchange:elvo.wallet.exchange}") String exchange,
                                @Value("${elvo.messaging.wallet.version:v1}") String version) {
        this(rabbitTemplate, exchange, version, null, null, null);
    }

    @Autowired
    public WalletEventPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${elvo.messaging.wallet.exchange:elvo.wallet.exchange}") String exchange,
                                @Value("${elvo.messaging.wallet.version:v1}") String version,
                                @Nullable SentryExceptionReporter sentryExceptionReporter,
                                @Nullable WalletMetricsRecorder metricsRecorder,
                                @Nullable ImmutableAuditStorageService immutableAuditStorageService) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.version = version;
        this.sentryExceptionReporter = sentryExceptionReporter;
        this.metricsRecorder = metricsRecorder;
        this.immutableAuditStorageService = immutableAuditStorageService;
    }

    public void publish(String eventType, Map<String, Object> payload) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("version", version);
        event.put("requestId", resolveRequestId());
        event.put("correlationId", resolveCorrelationId());
        event.put("occurredAt", Instant.now().toString());
        event.put("payload", payload == null ? Map.of() : payload);

        if (immutableAuditStorageService != null) {
            try {
                immutableAuditStorageService.append(
                        String.valueOf(event.get("eventType")),
                        String.valueOf(event.get("requestId")),
                        String.valueOf(event.get("correlationId")),
                        Instant.parse(String.valueOf(event.get("occurredAt"))),
                        String.valueOf(event.get("payload"))
                );
            } catch (RuntimeException ex) {
                // Preserve core transaction flow even if audit persistence is temporarily unavailable.
            }
        }

        try {
            rabbitTemplate.convertAndSend(exchange, eventType, event);
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

    private String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    private String resolveCorrelationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}
