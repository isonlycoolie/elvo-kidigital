package com.elvo.billing.service.event.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.elvo.billing.security.BillingServiceAuthorizationMatrix;
import com.elvo.billing.security.InternalServiceMessageAuthenticator;
import com.elvo.billing.security.SensitiveDataMasker;
import com.elvo.billing.service.event.BillingEventPublisher;

@Component
public class BillingEventPublisherStub implements BillingEventPublisher {

    private static final Logger eventLog = LoggerFactory.getLogger("event.billing.publisher");
    private static final String SOURCE_SERVICE = "elvo-billing-service";
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final BillingServiceAuthorizationMatrix authorizationMatrix;

    public BillingEventPublisherStub(
            RabbitTemplate rabbitTemplate,
            BillingServiceAuthorizationMatrix authorizationMatrix,
            @Value("${elvo.messaging.billing.exchange:elvo.billing.exchange}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.authorizationMatrix = authorizationMatrix;
        this.exchange = exchange;
    }

    @Override
    public void publish(String eventType, String requestId, String payload, String eventVersion) {
        if (!authorizationMatrix.isAllowed("billing-service", "PUBLISH", eventType)) {
            throw new SecurityException("billing service is not allowed to publish event " + eventType);
        }

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("eventVersion", eventVersion);
        event.put("requestId", requestId);
        event.put("correlationId", resolveContextValue("correlationId"));
        Instant occurredAt = Instant.now();
        event.put("occurredAt", occurredAt.toString());
        event.put("messageId", UUID.randomUUID().toString());
        event.put("nonce", UUID.randomUUID().toString());
        event.put("expiresAt", occurredAt.plus(5, ChronoUnit.MINUTES).toString());
        event.put("payload", payload == null ? "{}" : payload);

        Map<String, Object> signedEvent = InternalServiceMessageAuthenticator.signEvent(SOURCE_SERVICE, event);

        rabbitTemplate.convertAndSend(exchange, eventType, signedEvent);
        eventLog.info(
                "billing_event_published exchange={} eventType={} eventVersion={} requestId={} sourceService={}",
                exchange,
                eventType,
                eventVersion,
            SensitiveDataMasker.maskIdentifier(requestId),
                SOURCE_SERVICE);
    }

    private String resolveContextValue(String key) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? null : value;
    }
}
