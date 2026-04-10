package com.elvo.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elvo.messaging.account-intent")
public class AccountIntentMessagingProperties {

    private String eventVersion = "v1";
    private String exchange = "elvo.identity.account.exchange";
    private String routingKey = "elvo.identity.account.creation.intent";
    private String deadLetterExchange = "elvo.identity.account.dlx";
    private String deadLetterRoutingKey = "elvo.identity.account.dead";
    private String queue = "elvo.identity.account.queue";
    private String deadLetterQueue = "elvo.identity.account.dlq";
    private String retryQueue = "elvo.identity.account.retry.queue";
    private String retryRoutingKey = "elvo.identity.account.retry";
    private long retryDelayMs = 5000;
    private int publishMaxAttempts = 3;
    private long publishInitialIntervalMs = 200;
    private double publishMultiplier = 2.0;
    private long publishMaxIntervalMs = 2000;

    public String getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(String eventVersion) {
        this.eventVersion = eventVersion;
    }

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

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public String getRetryQueue() {
        return retryQueue;
    }

    public void setRetryQueue(String retryQueue) {
        this.retryQueue = retryQueue;
    }

    public String getRetryRoutingKey() {
        return retryRoutingKey;
    }

    public void setRetryRoutingKey(String retryRoutingKey) {
        this.retryRoutingKey = retryRoutingKey;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public int getPublishMaxAttempts() {
        return publishMaxAttempts;
    }

    public void setPublishMaxAttempts(int publishMaxAttempts) {
        this.publishMaxAttempts = publishMaxAttempts;
    }

    public long getPublishInitialIntervalMs() {
        return publishInitialIntervalMs;
    }

    public void setPublishInitialIntervalMs(long publishInitialIntervalMs) {
        this.publishInitialIntervalMs = publishInitialIntervalMs;
    }

    public double getPublishMultiplier() {
        return publishMultiplier;
    }

    public void setPublishMultiplier(double publishMultiplier) {
        this.publishMultiplier = publishMultiplier;
    }

    public long getPublishMaxIntervalMs() {
        return publishMaxIntervalMs;
    }

    public void setPublishMaxIntervalMs(long publishMaxIntervalMs) {
        this.publishMaxIntervalMs = publishMaxIntervalMs;
    }
}
