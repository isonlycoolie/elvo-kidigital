package com.elvo.billing.service.event;

public interface BillingEventPublisher {

    String TRANSACTION_REQUESTED = "billing.transaction.requested";
    String TRANSACTION_COMPLETED = "billing.transaction.completed";

    default void publish(String eventType, String requestId, String payload) {
        publish(eventType, requestId, payload, "v1");
    }

    default void publishTransactionRequested(String requestId, String payload) {
        publish(TRANSACTION_REQUESTED, requestId, payload, "v1");
    }

    default void publishTransactionCompleted(String requestId, String payload) {
        publish(TRANSACTION_COMPLETED, requestId, payload, "v1");
    }

    void publish(String eventType, String requestId, String payload, String eventVersion);
}
