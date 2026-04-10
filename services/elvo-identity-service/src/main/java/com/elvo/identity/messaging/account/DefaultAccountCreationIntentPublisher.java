package com.elvo.identity.messaging.account;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.elvo.identity.config.AccountIntentMessagingProperties;
import com.elvo.identity.config.CorrelationIdFilter;
import com.elvo.identity.dto.event.AccountCreationIntentEvent;
import com.elvo.identity.entity.User;

@Component
public class DefaultAccountCreationIntentPublisher implements AccountCreationIntentPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAccountCreationIntentPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RetryTemplate retryTemplate;
    private final AccountIntentMessagingProperties properties;

    public DefaultAccountCreationIntentPublisher(RabbitTemplate rabbitTemplate,
                                                 @Qualifier("accountIntentPublishRetryTemplate") RetryTemplate retryTemplate,
                                                 AccountIntentMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.retryTemplate = retryTemplate;
        this.properties = properties;
    }

    @Override
    @Async("accountIntentEventPublisherExecutor")
    public void publish(User user, String sourceIp, String sourceUserAgent) {
        AccountCreationIntentEvent event = toEvent(user, sourceIp, sourceUserAgent);

        retryTemplate.execute(context -> {
            rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), event, message -> {
                message.getMessageProperties().setHeader("eventVersion", event.eventVersion());
                message.getMessageProperties().setHeader("correlationId", event.correlationId());
                message.getMessageProperties().setHeader(
                        "occurredAt",
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(event.occurredAt().atOffset(ZoneOffset.UTC)));
                return message;
            });
            return null;
        }, context -> {
            rabbitTemplate.convertAndSend(properties.getDeadLetterExchange(), properties.getDeadLetterRoutingKey(), event);
            LOGGER.error("Account intent event {} routed to DLQ after {} attempts", event.eventId(), context.getRetryCount());
            return null;
        });

        LOGGER.info("Published account creation intent event {} exchange={} routingKey={} userId={}",
                event.eventId(), properties.getExchange(), properties.getRoutingKey(), event.userId());
    }

    private AccountCreationIntentEvent toEvent(User user, String sourceIp, String sourceUserAgent) {
        return new AccountCreationIntentEvent(
                UUID.randomUUID(),
                properties.getEventVersion(),
                Instant.now(),
                resolveCorrelationId(),
                user.getId(),
            null,
                user.getEmail(),
                user.getPhone(),
                user.getDisplayName(),
                user.isMfaEnabled(),
                "identity-service",
                sourceIp,
                sourceUserAgent);
    }

    private String resolveCorrelationId() {
        return MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
    }
}
