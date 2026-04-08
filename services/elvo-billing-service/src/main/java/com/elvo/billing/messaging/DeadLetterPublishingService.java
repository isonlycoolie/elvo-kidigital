package com.elvo.billing.messaging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.billing.security.SensitiveDataMasker;

@Service
public class DeadLetterPublishingService {

    private static final Logger DLQ_LOG = LoggerFactory.getLogger("audit.billing.dead-letter");

    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;
    private final String exchange;
    private final String routingKey;

    public DeadLetterPublishingService(
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            @Value("${elvo.messaging.dead-letter.enabled:true}") boolean enabled,
            @Value("${elvo.messaging.dead-letter.exchange:elvo.billing.dead-letter.exchange}") String exchange,
            @Value("${elvo.messaging.dead-letter.routing-key:elvo.billing.dead-letter}") String routingKey) {
        this.rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        this.enabled = enabled;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(String sourceQueue, String reason, Map<String, Object> event) {
        if (!enabled) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deadLetterId", UUID.randomUUID().toString());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("sourceQueue", sourceQueue == null ? "unknown" : sourceQueue);
        payload.put("reason", reason == null ? "unknown" : reason);
        payload.put("event", event == null ? Map.of() : event);

        DLQ_LOG.warn("billing_dead_letter_enqueued sourceQueue={} reason={} event={}",
                payload.get("sourceQueue"),
                payload.get("reason"),
                SensitiveDataMasker.maskText(String.valueOf(payload.get("event"))));

        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            } catch (RuntimeException ex) {
                DLQ_LOG.error("billing_dead_letter_publish_failed reason={}", SensitiveDataMasker.maskText(ex.getMessage()));
            }
        }
    }
}
