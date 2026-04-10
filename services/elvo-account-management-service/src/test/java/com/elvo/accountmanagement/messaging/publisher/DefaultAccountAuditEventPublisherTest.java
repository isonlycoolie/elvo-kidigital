package com.elvo.accountmanagement.messaging.publisher;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.elvo.accountmanagement.config.AccountAuditMessagingProperties;
import com.elvo.accountmanagement.entity.AccountAuditLog;
import com.elvo.accountmanagement.messaging.event.AccountAuditTrailEvent;

@ExtendWith(MockitoExtension.class)
class DefaultAccountAuditEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private DefaultAccountAuditEventPublisher publisher;

    @BeforeEach
    void setUp() {
        AccountAuditMessagingProperties properties = new AccountAuditMessagingProperties();
        properties.setEventVersion("v1");
        properties.setExchange("elvo.account.audit.exchange");
        properties.setRoutingKey("account.audit.v1");
        publisher = new DefaultAccountAuditEventPublisher(rabbitTemplate, properties);
    }

    @Test
    void publishSendsAuditEventToConfiguredRoute() {
        AccountAuditLog auditLog = new AccountAuditLog();
        setAuditLogId(auditLog, UUID.randomUUID());
        auditLog.setAccountId(UUID.randomUUID());
        auditLog.setActionType("ACCOUNT_CREATED");
        auditLog.setDescription("Account created");
        auditLog.setRequestId("req-1");
        auditLog.setCorrelationId("corr-1");
        auditLog.setSourceService("identity-service");
        auditLog.setSourceIp("127.0.0.1");
        auditLog.setSourceUserAgent("identity");
        auditLog.setCreatedBy("system");

        publisher.publish(auditLog);

        verify(rabbitTemplate).convertAndSend(
                eq("elvo.account.audit.exchange"),
                eq("account.audit.v1"),
                org.mockito.ArgumentMatchers.any(AccountAuditTrailEvent.class));
    }

    private static void setAuditLogId(AccountAuditLog auditLog, UUID auditLogId) {
        try {
            var field = AccountAuditLog.class.getDeclaredField("auditLogId");
            field.setAccessible(true);
            field.set(auditLog, auditLogId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
