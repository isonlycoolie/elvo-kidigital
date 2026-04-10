package com.elvo.accountmanagement.messaging.publisher;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.elvo.accountmanagement.config.AccountAuditMessagingProperties;
import com.elvo.accountmanagement.entity.AccountAuditLog;
import com.elvo.accountmanagement.messaging.event.AccountAuditTrailEvent;

@Component
public class DefaultAccountAuditEventPublisher implements AccountAuditEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAccountAuditEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final AccountAuditMessagingProperties properties;

    public DefaultAccountAuditEventPublisher(RabbitTemplate rabbitTemplate,
                                             AccountAuditMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(AccountAuditLog auditLog) {
        if (auditLog == null || auditLog.getAuditLogId() == null) {
            return;
        }

        AccountAuditTrailEvent event = new AccountAuditTrailEvent(
                UUID.randomUUID(),
                properties.getEventVersion(),
                auditLog.getAuditLogId(),
                auditLog.getAccountId(),
                auditLog.getActionType(),
                auditLog.getDescription(),
                auditLog.getRequestId(),
                auditLog.getCorrelationId(),
                auditLog.getSourceService(),
                auditLog.getSourceIp(),
                auditLog.getSourceUserAgent(),
                auditLog.getCreatedBy(),
                auditLog.getCreatedAt());

        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), event);
        LOG.info("Published account audit event auditLogId={} action={} eventVersion={}",
                auditLog.getAuditLogId(),
                auditLog.getActionType(),
                properties.getEventVersion());
    }
}
