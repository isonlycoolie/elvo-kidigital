package com.elvo.billing.service.event;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.BillLookup;

/**
 * Interface for publishing billing domain events.
 * Implementations will handle async event delivery via RabbitMQ, Kafka, or other messaging.
 */
public interface BillingEventPublisher {

    /**
     * Publish a payment.requested event when a payment is created.
     * 
     * @param payment the persisted BillPayment
     */
    void publishPaymentRequested(BillPayment payment);

    /**
     * Publish a payment.completed event when a payment succeeds.
     * 
     * @param payment the persisted BillPayment with SUCCESS status
     */
    void publishPaymentCompleted(BillPayment payment);

    /**
     * Publish a payment.failed event when a payment fails.
     * 
     * @param payment the persisted BillPayment
     * @param reason the failure reason
     */
    void publishPaymentFailed(BillPayment payment, String reason);

    /**
     * Publish a payment.reversed event when a payment is reversed.
     * 
     * @param payment the persisted BillPayment with REVERSED status
     */
    void publishPaymentReversed(BillPayment payment);

    /**
     * Publish a lookup.completed event when a lookup succeeds.
     * 
     * @param lookup the persisted BillLookup
     */
    void publishLookupCompleted(BillLookup lookup);
}
