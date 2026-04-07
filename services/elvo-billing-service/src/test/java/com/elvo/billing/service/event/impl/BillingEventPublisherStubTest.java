package com.elvo.billing.service.event.impl;

import com.elvo.billing.service.event.BillingEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BillingEventPublisherStubTest {

    @Test
    void shouldPublishExplicitEventTypeToRabbitMq() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        BillingEventPublisherStub publisher = new BillingEventPublisherStub(rabbitTemplate, "elvo.billing.exchange");

        publisher.publish("billing.payment.reversed", "req-1", "{\"paymentId\":\"p-1\"}", "v2");

        verify(rabbitTemplate).convertAndSend(eq("elvo.billing.exchange"), eq("billing.payment.reversed"), any());
    }

    @Test
    void shouldPublishRequestedAndCompletedTransactionEvents() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        BillingEventPublisher publisher = new BillingEventPublisherStub(rabbitTemplate, "elvo.billing.exchange");

        publisher.publishTransactionRequested("req-2", "{\"transactionId\":\"t-1\"}");
        publisher.publishTransactionCompleted("req-2", "{\"transactionId\":\"t-1\",\"status\":\"SUCCESS\"}");

        verify(rabbitTemplate).convertAndSend(eq("elvo.billing.exchange"), eq(BillingEventPublisher.TRANSACTION_REQUESTED), any());
        verify(rabbitTemplate).convertAndSend(eq("elvo.billing.exchange"), eq(BillingEventPublisher.TRANSACTION_COMPLETED), any());
        verify(rabbitTemplate, times(2)).convertAndSend(eq("elvo.billing.exchange"), any(), any());
    }
}
