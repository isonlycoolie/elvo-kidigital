package com.elvo.wallet.messaging.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.elvo.wallet.messaging.outbox.WalletOutboxService.OutboxEvent;
import com.elvo.wallet.messaging.outbox.WalletOutboxService.Status;
import com.elvo.wallet.monitoring.SentryExceptionReporter;
import com.elvo.wallet.monitoring.WalletMetricsRecorder;

class WalletOutboxDispatcherTest {

    @Test
    void replayDeadLetterShouldRepublishMatchingRoutingKeys() {
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        WalletOutboxService outboxService = org.mockito.Mockito.mock(WalletOutboxService.class);
        WalletMetricsRecorder metrics = org.mockito.Mockito.mock(WalletMetricsRecorder.class);
        SentryExceptionReporter sentry = org.mockito.Mockito.mock(SentryExceptionReporter.class);

        WalletOutboxDispatcher dispatcher = new WalletOutboxDispatcher(
                rabbitTemplate,
                "wallet.exchange",
                outboxService,
                metrics,
                sentry,
                5,
                2);

        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                eventId,
                "wallet.transfer.failed",
                "wallet.transfer.failed",
                Map.of("eventType", "wallet.transfer.failed"),
                "req-1",
                "corr-1",
                Instant.now(),
                null,
                Status.DEAD_LETTER,
                5,
                "broken",
                Instant.now());

        when(outboxService.lockBatchForReplay(eq(Status.DEAD_LETTER), eq(25), eq("wallet.transfer")))
                .thenReturn(List.of(event));
        when(outboxService.lockForDispatch(eventId)).thenReturn(java.util.Optional.of(event));

        int replayed = dispatcher.replayDeadLetter(25, "wallet.transfer");

        org.assertj.core.api.Assertions.assertThat(replayed).isEqualTo(1);
        verify(outboxService).markDispatchFailure(eq(eventId), eq(0), eq(5), any(), eq(null));
        verify(rabbitTemplate).convertAndSend(
                eq("wallet.exchange"),
                eq("wallet.transfer.failed"),
                eq(event.payload()),
                any(MessagePostProcessor.class));
        verify(outboxService).markPublished(eq(eventId), any());
    }

    @Test
    void dispatchByIdShouldMarkFailureWhenBrokerThrows() {
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        WalletOutboxService outboxService = org.mockito.Mockito.mock(WalletOutboxService.class);
        WalletMetricsRecorder metrics = org.mockito.Mockito.mock(WalletMetricsRecorder.class);
        SentryExceptionReporter sentry = org.mockito.Mockito.mock(SentryExceptionReporter.class);

        WalletOutboxDispatcher dispatcher = new WalletOutboxDispatcher(
                rabbitTemplate,
                "wallet.exchange",
                outboxService,
                metrics,
                sentry,
                4,
                2);

        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                eventId,
                "wallet.transfer.completed",
                "wallet.transfer.completed",
                Map.of("eventType", "wallet.transfer.completed"),
                "req-2",
                "corr-2",
                Instant.now(),
                null,
                Status.PENDING,
                1,
                null,
                Instant.now());

        when(outboxService.lockForDispatch(eventId)).thenReturn(java.util.Optional.of(event));
        org.mockito.Mockito.doThrow(new AmqpException("down") {}).when(rabbitTemplate)
                .convertAndSend(
                        eq("wallet.exchange"),
                        eq("wallet.transfer.completed"),
                        eq(event.payload()),
                        any(MessagePostProcessor.class));

        dispatcher.dispatchById(eventId);

        verify(outboxService).markDispatchFailure(eq(eventId), eq(2), eq(4), any(), eq("down"));
        verify(outboxService, never()).markPublished(eq(eventId), any());
    }
}
