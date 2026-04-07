package com.elvo.billing.service.event.impl;

import com.elvo.billing.service.event.BillingEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class BillingEventPublisherStub implements BillingEventPublisher {

    private static final Logger eventLog = LoggerFactory.getLogger("event.billing.publisher");
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public BillingEventPublisherStub(
            RabbitTemplate rabbitTemplate,
            @Value("${elvo.messaging.billing.exchange:elvo.billing.exchange}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Override
    public void publish(String eventType, String requestId, String payload, String eventVersion) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("eventVersion", eventVersion);
        event.put("requestId", requestId);
        event.put("occurredAt", Instant.now().toString());
        event.put("payload", payload == null ? "{}" : payload);

        rabbitTemplate.convertAndSend(exchange, eventType, event);
        eventLog.info(
                "billing_event_published exchange={} eventType={} eventVersion={} requestId={}",
                exchange,
                eventType,
                eventVersion,
                requestId);
    }
}
