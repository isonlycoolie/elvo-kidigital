package com.elvo.accountmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elvo.messaging.identity-registration")
public class IdentityRegistrationMessagingProperties {

    private String exchange = "elvo.identity.account.exchange";
    private String routingKey = "elvo.identity.account.creation.intent";
    private String queue = "elvo.account.identity-registration.queue";
    private String deadLetterExchange = "elvo.account.identity-registration.dlx";
    private String deadLetterRoutingKey = "elvo.account.identity-registration.dead";
    private String deadLetterQueue = "elvo.account.identity-registration.dlq";

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getDeadLetterRoutingKey() {
        return deadLetterRoutingKey;
    }

    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
        this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }
}
