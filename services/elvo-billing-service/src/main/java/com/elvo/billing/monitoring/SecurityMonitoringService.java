package com.elvo.billing.monitoring;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.billing.security.SensitiveDataMasker;

@Service
public class SecurityMonitoringService {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("audit.billing.security.monitor");

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final boolean enabled;
    private final int repeatedReversalAlertThreshold;
    private final ConcurrentHashMap<String, AtomicInteger> reversalAttempts = new ConcurrentHashMap<>();

    public SecurityMonitoringService(ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                                     @Value("${elvo.security.monitoring.enabled:true}") boolean enabled,
                                     @Value("${elvo.security.monitoring.exchange:elvo.billing.security.exchange}") String exchange,
                                     @Value("${elvo.security.monitoring.routing-key:elvo.billing.security.alert}") String routingKey,
                                     @Value("${elvo.security.monitoring.reversal-threshold:3}") int repeatedReversalAlertThreshold) {
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.enabled = enabled;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.repeatedReversalAlertThreshold = Math.max(2, repeatedReversalAlertThreshold);
    }

    public void recordInvalidSignature(String sourceService, String operation) {
        stream("billing.security.invalid_signature", "HIGH", Map.of(
                "sourceService", safe(sourceService),
                "operation", safe(operation)));
    }

    public void recordReplayAttempt(String sourceService, String operation) {
        stream("billing.security.replay_attempt", "HIGH", Map.of(
                "sourceService", safe(sourceService),
                "operation", safe(operation)));
    }

    public void recordPrivilegeEscalationAttempt(String principal, String permission) {
        stream("billing.security.privilege_escalation_attempt", "HIGH", Map.of(
                "principal", SensitiveDataMasker.maskIdentifier(safe(principal)),
                "permission", safe(permission)));
    }

    public void recordRepeatedReversalAttempt(String referenceNumber) {
        String key = safe(referenceNumber);
        if (key.isBlank()) {
            return;
        }
        int attempts = reversalAttempts.computeIfAbsent(key, ignored -> new AtomicInteger(0)).incrementAndGet();
        if (attempts >= repeatedReversalAlertThreshold) {
            stream("billing.security.repeated_reversal_attempt", "MEDIUM", Map.of(
                    "referenceNumber", SensitiveDataMasker.maskIdentifier(key),
                    "attempts", attempts));
        }
    }

    public void recordSuspiciousEvent(String eventType, String reason, Map<String, Object> details) {
        stream(eventType, "WARN", Map.of(
                "reason", safe(reason),
                "details", details == null ? Map.of() : details));
    }

    private void stream(String eventType, String severity, Map<String, Object> context) {
        if (!enabled) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", safe(eventType),
                "severity", safe(severity),
                "occurredAt", Instant.now().toString(),
                "context", context == null ? Map.of() : context);

        SECURITY_LOG.warn("billing_security_alert eventType={} severity={} context={}",
                payload.get("eventType"),
                payload.get("severity"),
                SensitiveDataMasker.maskText(String.valueOf(payload.get("context"))));

        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            } catch (RuntimeException ex) {
                SECURITY_LOG.error("billing_security_alert_publish_failed reason={}", SensitiveDataMasker.maskText(ex.getMessage()));
            }
        }
    }

    private String safe(String value) {
        return value == null ? "unknown" : value;
    }
}