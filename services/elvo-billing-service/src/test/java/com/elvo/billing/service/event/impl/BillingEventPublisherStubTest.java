package com.elvo.billing.service.event.impl;

import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.security.BillingServiceAuthorizationMatrix;
import com.elvo.billing.security.BillingServiceAuthorizationProperties;
import com.elvo.billing.security.InternalServiceMessageAuthenticator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BillingEventPublisherStubTest {

    @Test
    void shouldPublishExplicitEventTypeToRabbitMq() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        BillingEventPublisherStub publisher = new BillingEventPublisherStub(
            rabbitTemplate,
            new BillingServiceAuthorizationMatrix(new BillingServiceAuthorizationProperties()),
            "elvo.billing.exchange");

        publisher.publish("billing.payment.reversed", "req-1", "{\"paymentId\":\"p-1\"}", "v2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(eq("elvo.billing.exchange"), eq("billing.payment.reversed"), captor.capture());

        Map<String, Object> event = captor.getValue();
        assertSignedEvent(event, "elvo-billing-service");
    }

    @Test
    void shouldPublishRequestedAndCompletedTransactionEvents() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        BillingEventPublisher publisher = new BillingEventPublisherStub(
            rabbitTemplate,
            new BillingServiceAuthorizationMatrix(new BillingServiceAuthorizationProperties()),
            "elvo.billing.exchange");

        publisher.publishTransactionRequested("req-2", "{\"transactionId\":\"t-1\"}");
        publisher.publishTransactionCompleted("req-2", "{\"transactionId\":\"t-1\",\"status\":\"SUCCESS\"}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(eq("elvo.billing.exchange"), eq(BillingEventPublisher.TRANSACTION_REQUESTED), captor.capture());
        verify(rabbitTemplate).convertAndSend(eq("elvo.billing.exchange"), eq(BillingEventPublisher.TRANSACTION_COMPLETED), captor.capture());

        for (Map<String, Object> event : captor.getAllValues()) {
            assertSignedEvent(event, "elvo-billing-service");
        }
    }

    private void assertSignedEvent(Map<String, Object> event, String expectedSourceService) {
        org.assertj.core.api.Assertions.assertThat(event.get("sourceService")).isEqualTo(expectedSourceService);
        org.assertj.core.api.Assertions.assertThat(InternalServiceMessageAuthenticator.isTrusted(event, expectedSourceService)).isTrue();
    }
}
