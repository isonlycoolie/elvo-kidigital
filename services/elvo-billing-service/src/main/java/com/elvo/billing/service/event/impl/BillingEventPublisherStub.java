package com.elvo.billing.service.event.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.BillLookup;
import com.elvo.billing.service.event.BillingEventPublisher;

/**
 * Stub implementation of BillingEventPublisher for Phase 6.
 * 
 * In Phase 8 (Messaging), this will be replaced with a full implementation
 * that publishes to RabbitMQ or Kafka.
 */
@Component
public class BillingEventPublisherStub implements BillingEventPublisher {

    private static final Logger eventLog = LoggerFactory.getLogger("event.billing");

    @Override
    public void publishPaymentRequested(BillPayment payment) {
        eventLog.info("EVENT: billing.payment.requested | paymentId={} | referenceNumber={} | amount={}", 
                payment.getPaymentId(), payment.getReferenceNumber(), payment.getAmount());
    }

    @Override
    public void publishPaymentCompleted(BillPayment payment) {
        eventLog.info("EVENT: billing.payment.completed | paymentId={} | externalReference={} | status=SUCCESS", 
                payment.getPaymentId(), payment.getExternalReference());
    }

    @Override
    public void publishPaymentFailed(BillPayment payment, String reason) {
        eventLog.warn("EVENT: billing.payment.failed | paymentId={} | reason={} | status=FAILED", 
                payment.getPaymentId(), reason);
    }

    @Override
    public void publishPaymentReversed(BillPayment payment) {
        eventLog.info("EVENT: billing.payment.reversed | paymentId={} | externalReference={} | status=REVERSED", 
                payment.getPaymentId(), payment.getExternalReference());
    }

    @Override
    public void publishLookupCompleted(BillLookup lookup) {
        eventLog.info("EVENT: billing.lookup.completed | lookupId={} | referenceNumber={} | lookupStatus={}", 
                lookup.getLookupId(), lookup.getReferenceNumber(), lookup.getLookupStatus());
    }
}
