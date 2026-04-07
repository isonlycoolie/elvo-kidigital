package com.elvo.billing.service.event;

public interface BillingEventPublisher {

    void publish(String eventType, String requestId, String payload);
}
