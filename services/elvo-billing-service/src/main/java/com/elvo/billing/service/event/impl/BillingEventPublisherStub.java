package com.elvo.billing.service.event.impl;

import com.elvo.billing.service.event.BillingEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BillingEventPublisherStub implements BillingEventPublisher {

    private static final Logger eventLog = LoggerFactory.getLogger("event.billing.publisher");

    @Override
    public void publish(String eventType, String requestId, String payload, String eventVersion) {
        eventLog.info(
                "billing_event eventType={} eventVersion={} requestId={} payload={}",
                eventType,
                eventVersion,
                requestId,
                payload);
    }
}
