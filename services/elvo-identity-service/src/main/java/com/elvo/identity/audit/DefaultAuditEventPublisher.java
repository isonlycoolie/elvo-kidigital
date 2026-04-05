package com.elvo.identity.audit;

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

import com.elvo.identity.config.CorrelationIdFilter;
import com.elvo.identity.config.AuditMessagingProperties;
import com.elvo.identity.dto.event.AuditEvent;
import com.elvo.identity.entity.Audit;

@Component
public class DefaultAuditEventPublisher implements AuditEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAuditEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RetryTemplate retryTemplate;
    private final AuditMessagingProperties properties;

    public DefaultAuditEventPublisher(RabbitTemplate rabbitTemplate,
                                      @Qualifier("auditPublishRetryTemplate") RetryTemplate retryTemplate,
                                      AuditMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.retryTemplate = retryTemplate;
        this.properties = properties;
    }

    @Override
    @Async("auditEventPublisherExecutor")
    public void publish(Audit audit) {
        AuditEvent event = toAuditEvent(audit);

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
            LOGGER.error("Audit event {} routed to DLQ after {} attempts", event.eventId(), context.getRetryCount());
            return null;
        });

        LOGGER.info("Published audit event {} action={} exchange={} routingKey={}",
                event.eventId(), event.actionType(), properties.getExchange(), properties.getRoutingKey());
    }

    private AuditEvent toAuditEvent(Audit audit) {
        String correlationId = resolveCorrelationId(audit);
        Instant occurredAt = audit.getCreatedAt() != null ? audit.getCreatedAt() : Instant.now();
        UUID userId = audit.getUser() != null ? audit.getUser().getId() : null;

        return new AuditEvent(
                UUID.randomUUID(),
            properties.getEventVersion(),
                occurredAt,
                correlationId,
                audit.getActionType().name(),
                audit.getDescription(),
                audit.getSourceType().name(),
                audit.getSourceIp(),
                audit.getSourceUserAgent(),
                userId,
                audit.getSessionId(),
                audit.getDeviceId());
    }

    private String resolveCorrelationId(Audit audit) {
        if (audit.getCorrelationId() != null && !audit.getCorrelationId().isBlank()) {
            return audit.getCorrelationId();
        }
        return MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
    }
}
