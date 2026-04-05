package com.elvo.wallet.messaging.producer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WalletEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String version;

    public WalletEventPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${elvo.messaging.wallet.exchange:elvo.wallet.exchange}") String exchange,
                                @Value("${elvo.messaging.wallet.version:v1}") String version) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.version = version;
    }

    public void publish(String eventType, Map<String, Object> payload) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("version", version);
        event.put("requestId", resolveRequestId());
        event.put("correlationId", resolveCorrelationId());
        event.put("occurredAt", Instant.now().toString());
        event.put("payload", payload == null ? Map.of() : payload);
        rabbitTemplate.convertAndSend(exchange, eventType, event);
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
