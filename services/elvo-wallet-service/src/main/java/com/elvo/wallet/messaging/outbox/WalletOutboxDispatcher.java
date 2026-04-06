package com.elvo.wallet.messaging.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.elvo.wallet.messaging.outbox.WalletOutboxService.OutboxEvent;
import com.elvo.wallet.messaging.outbox.WalletOutboxService.Status;
import com.elvo.wallet.monitoring.SentryExceptionReporter;
import com.elvo.wallet.monitoring.WalletMetricsRecorder;

@Component
public class WalletOutboxDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final WalletOutboxService outboxService;
    private final WalletMetricsRecorder metricsRecorder;
    private final SentryExceptionReporter sentryExceptionReporter;
    private final int maxAttempts;
    private final Duration baseBackoff;

    public WalletOutboxDispatcher(
            RabbitTemplate rabbitTemplate,
            @Value("${elvo.messaging.wallet.exchange:elvo.wallet.exchange}") String exchange,
            WalletOutboxService outboxService,
            WalletMetricsRecorder metricsRecorder,
            SentryExceptionReporter sentryExceptionReporter,
            @Value("${elvo.messaging.outbox.max-attempts:6}") int maxAttempts,
            @Value("${elvo.messaging.outbox.base-backoff-seconds:2}") int baseBackoffSeconds) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.outboxService = outboxService;
        this.metricsRecorder = metricsRecorder;
        this.sentryExceptionReporter = sentryExceptionReporter;
        this.maxAttempts = Math.max(2, maxAttempts);
        this.baseBackoff = Duration.ofSeconds(Math.max(1, baseBackoffSeconds));
    }

    public void dispatchById(UUID eventId) {
        outboxService.lockForDispatch(eventId).ifPresent(this::dispatch);
    }

    public int replayDeadLetter(int batchSize, String routingKeyPrefix) {
        List<OutboxEvent> events = outboxService.lockBatchForReplay(Status.DEAD_LETTER, batchSize, routingKeyPrefix);
        events.forEach(this::dispatchFromDeadLetter);
        return events.size();
    }

    private void dispatchFromDeadLetter(OutboxEvent event) {
        outboxService.markDispatchFailure(event.eventId(), 0, maxAttempts, baseBackoff, null);
        outboxService.lockForDispatch(event.eventId()).ifPresent(this::dispatch);
    }

    private void dispatch(OutboxEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, event.routingKey(), event.payload(), messageIdProcessor(event.eventId()));
            outboxService.markPublished(event.eventId(), Instant.now());
            if (metricsRecorder != null) {
                metricsRecorder.recordEventPublish(event.eventType(), true);
            }
        } catch (AmqpException ex) {
            int nextAttempts = event.attemptCount() + 1;
            outboxService.markDispatchFailure(event.eventId(), nextAttempts, maxAttempts, baseBackoff, ex.getMessage());
            if (metricsRecorder != null) {
                metricsRecorder.recordEventPublish(event.eventType(), false);
            }
            if (sentryExceptionReporter != null) {
                sentryExceptionReporter.captureCriticalException(
                        ex,
                        null,
                        java.util.Map.of(
                                "eventType", String.valueOf(event.eventType()),
                                "eventId", String.valueOf(event.eventId()),
                                "routingKey", String.valueOf(event.routingKey())));
            }
        }
    }

    private MessagePostProcessor messageIdProcessor(UUID eventId) {
        return (Message message) -> {
            message.getMessageProperties().setMessageId(String.valueOf(eventId));
            return message;
        };
    }
}
