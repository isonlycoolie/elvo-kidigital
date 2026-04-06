package com.elvo.wallet.monitoring;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Service
public class SecurityAlertStreamingService {

    private static final Logger ALERT_LOG = LoggerFactory.getLogger("audit.wallet.security.alert");

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final boolean enabled;

    public SecurityAlertStreamingService(ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                                         @Value("${elvo.security.alert-stream.enabled:true}") boolean enabled,
                                         @Value("${elvo.security.alert-stream.exchange:elvo.wallet.security.alert.exchange}") String exchange,
                                         @Value("${elvo.security.alert-stream.routing-key:elvo.wallet.security.alert}") String routingKey) {
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.enabled = enabled;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void stream(String eventType, String severity, UUID userId, Map<String, Object> context) {
        if (!enabled) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", eventType == null ? "wallet.security.unknown" : eventType,
                "severity", severity == null ? "WARN" : severity,
                "occurredAt", Instant.now().toString(),
                "userId", userId == null ? "unknown" : userId.toString(),
                "context", context == null ? Map.of() : context);

        ALERT_LOG.warn("security_alert_stream eventType={} severity={} userId={} context={}",
                payload.get("eventType"),
                payload.get("severity"),
                payload.get("userId"),
                payload.get("context"));

        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            } catch (RuntimeException ex) {
                ALERT_LOG.error("security_alert_stream_publish_failed reason={}", ex.getMessage());
            }
        }
    }
}
