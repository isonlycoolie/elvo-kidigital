package com.elvo.accountmanagement.messaging.publisher;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.elvo.accountmanagement.config.AccountEventMessagingProperties;
import com.elvo.accountmanagement.entity.Account;
import com.elvo.accountmanagement.messaging.event.AccountLifecyclePolicyEvent;

@Component
public class DefaultAccountEventPublisher implements AccountEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAccountEventPublisher.class);
    private static final String EVENT_VERSION = "v1";

    private final RabbitTemplate rabbitTemplate;
    private final AccountEventMessagingProperties properties;

    public DefaultAccountEventPublisher(RabbitTemplate rabbitTemplate,
                                        AccountEventMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publishLifecycle(Account account,
                                 String eventType,
                                 String reason,
                                 String requestId,
                                 String correlationId,
                                 String sourceService,
                                 String sourceIp,
                                 String sourceUserAgent,
                                 String actor) {
        publish(account, "LIFECYCLE", eventType, reason, requestId, correlationId, sourceService, sourceIp, sourceUserAgent, actor, properties.getLifecycleRoutingKey());
    }

    @Override
    public void publishPolicy(Account account,
                              String eventType,
                              String reason,
                              String requestId,
                              String correlationId,
                              String sourceService,
                              String sourceIp,
                              String sourceUserAgent,
                              String actor) {
        publish(account, "POLICY", eventType, reason, requestId, correlationId, sourceService, sourceIp, sourceUserAgent, actor, properties.getPolicyRoutingKey());
    }

    private void publish(Account account,
                         String category,
                         String eventType,
                         String reason,
                         String requestId,
                         String correlationId,
                         String sourceService,
                         String sourceIp,
                         String sourceUserAgent,
                         String actor,
                         String routingKey) {
        if (account == null || account.getAccountId() == null) {
            return;
        }

        AccountLifecyclePolicyEvent event = new AccountLifecyclePolicyEvent(
                UUID.randomUUID(),
                EVENT_VERSION,
                category,
                eventType,
                account.getAccountId(),
                account.getAccountStatus() == null ? null : account.getAccountStatus().name(),
                account.getKycStatus() == null ? null : account.getKycStatus().name(),
                reason,
                requestId,
                correlationId,
                sourceService,
                sourceIp,
                sourceUserAgent,
                actor,
                Instant.now());

        rabbitTemplate.convertAndSend(properties.getExchange(), routingKey, event);
        LOG.info("Published account {} event type={} accountId={} eventVersion={}", category.toLowerCase(), eventType, account.getAccountId(), EVENT_VERSION);
    }
}
