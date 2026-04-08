package com.elvo.wallet.messaging.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.elvo.wallet.messaging.outbox.WalletOutboxDispatcher;
import com.elvo.wallet.messaging.outbox.WalletOutboxService;
import com.elvo.wallet.security.InternalServiceMessageAuthenticator;

import static org.mockito.Mockito.mock;

class WalletEventPublisherTest {

    @Test
    void publishShouldIncludeCorrelationAndRequestIds() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        WalletEventPublisher publisher = new WalletEventPublisher(rabbitTemplate, "wallet.exchange", "v1");

        MDC.put("requestId", "req-123");
        MDC.put("correlationId", "corr-456");
        try {
            publisher.publish("wallet.deposit.completed", Map.of("walletId", "w-1", "amount", "10.00"));
        } finally {
            MDC.clear();
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(eq("wallet.exchange"), eq("wallet.deposit.completed"), captor.capture());

        Map<String, Object> event = captor.getValue();
        assertThat(event.get("eventType")).isEqualTo("wallet.deposit.completed");
        assertThat(event.get("version")).isEqualTo("v1");
        assertThat(event.get("requestId")).isEqualTo("req-123");
        assertThat(event.get("correlationId")).isEqualTo("corr-456");
        assertThat(event.get("payload")).isInstanceOf(Map.class);
        assertThat(event.get("sourceService")).isEqualTo("elvo-wallet-service");
        assertThat(InternalServiceMessageAuthenticator.isTrusted(event, "elvo-wallet-service")).isTrue();
    }

    @Test
    void publishShouldPersistToOutboxWhenConfigured() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        WalletOutboxService outboxService = mock(WalletOutboxService.class);
        WalletOutboxDispatcher dispatcher = mock(WalletOutboxDispatcher.class);
        when(outboxService.enqueue(anyString(), anyString(), anyMap(), anyString(), anyString(), any()))
                .thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));

        WalletEventPublisher publisher = new WalletEventPublisher(
                rabbitTemplate,
                "wallet.exchange",
                "v1",
                null,
                null,
                null,
                outboxService,
                dispatcher);

        MDC.put("requestId", "req-1");
        MDC.put("correlationId", "corr-1");
        try {
            publisher.publish("wallet.transfer.completed", Map.of("walletId", "w-1"));
        } finally {
            MDC.clear();
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxService).enqueue(eq("wallet.transfer.completed"), eq("wallet.transfer.completed"), eventCaptor.capture(), eq("req-1"), eq("corr-1"), any());
        assertThat(eventCaptor.getValue().get("sourceService")).isEqualTo("elvo-wallet-service");
        assertThat(InternalServiceMessageAuthenticator.isTrusted(eventCaptor.getValue(), "elvo-wallet-service")).isTrue();
        verify(dispatcher).dispatchById(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyMap());
    }
}
