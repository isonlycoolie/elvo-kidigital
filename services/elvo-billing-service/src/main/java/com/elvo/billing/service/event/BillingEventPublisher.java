package com.elvo.billing.service.event;

public interface BillingEventPublisher {

    default void publish(String eventType, String requestId, String payload) {
        publish(eventType, requestId, payload, "v1");
    }

    void publish(String eventType, String requestId, String payload, String eventVersion);
}
