package com.elvo.identity.audit;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.elvo.identity.config.CorrelationIdFilter;
import com.elvo.identity.dto.event.AuditEvent;
import com.elvo.identity.entity.Audit;

@Component
public class DefaultAuditEventPublisher implements AuditEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAuditEventPublisher.class);
    private static final String EVENT_VERSION = "v1";

    @Override
    public void publish(Audit audit) {
        AuditEvent event = toAuditEvent(audit);
        LOGGER.info("Published audit event {} action={} correlationId={} occurredAt={}",
                event.eventId(), event.actionType(), event.correlationId(), event.occurredAt());
    }

    private AuditEvent toAuditEvent(Audit audit) {
        String correlationId = resolveCorrelationId(audit);
        Instant occurredAt = audit.getCreatedAt() != null ? audit.getCreatedAt() : Instant.now();
        UUID userId = audit.getUser() != null ? audit.getUser().getId() : null;

        return new AuditEvent(
                UUID.randomUUID(),
                EVENT_VERSION,
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
