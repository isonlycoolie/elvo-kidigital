package com.elvo.billing.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;

class CircuitBreakerBillingAdapterTest {

    @Test
    void shouldRetryAndEventuallySucceed() {
        AtomicInteger attempts = new AtomicInteger();
        BillingAdapter delegate = new BillingAdapter() {
            @Override
            public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("transient lookup failure");
                }
                return new LookupResponseDto();
            }

            @Override
            public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
                return new PaymentResponseDto();
            }
        };

        CircuitBreakerBillingAdapter adapter = new CircuitBreakerBillingAdapter(delegate, 3, 0L, 2, Duration.ofSeconds(1));

        assertThat(adapter.lookup(new UtilityPaymentRequestDto())).isNotNull();
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldOpenCircuitAfterFailedExecution() {
        AtomicInteger attempts = new AtomicInteger();
        BillingAdapter delegate = new BillingAdapter() {
            @Override
            public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
                attempts.incrementAndGet();
                throw new IllegalStateException("lookup failed");
            }

            @Override
            public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
                attempts.incrementAndGet();
                throw new IllegalStateException("pay failed");
            }
        };

        CircuitBreakerBillingAdapter adapter = new CircuitBreakerBillingAdapter(delegate, 2, 0L, 1, Duration.ofMinutes(1));

        assertThatThrownBy(() -> adapter.pay(new UtilityPaymentRequestDto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pay failed");

        assertThatThrownBy(() -> adapter.pay(new UtilityPaymentRequestDto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circuit breaker is open");

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldFailFastWhenExecutionExceedsTimeout() {
        BillingAdapter delegate = new BillingAdapter() {
            @Override
            public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
                return new LookupResponseDto();
            }

            @Override
            public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
                try {
                    Thread.sleep(250L);
                    return new PaymentResponseDto();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("sleep interrupted", ex);
                }
            }
        };

        CircuitBreakerBillingAdapter adapter = new CircuitBreakerBillingAdapter(delegate, 1, 0L, 1, Duration.ofMinutes(1), Duration.ofMillis(25));

        assertThatThrownBy(() -> adapter.pay(new UtilityPaymentRequestDto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void shouldEmitAuditLogsForRetryFailures() {
        Logger logger = (Logger) LoggerFactory.getLogger("audit.billing.adapter");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);

        try {
            BillingAdapter delegate = new BillingAdapter() {
                @Override
                public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
                    throw new IllegalStateException("lookup failed");
                }

                @Override
                public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
                    throw new IllegalStateException("pay failed");
                }
            };

            CircuitBreakerBillingAdapter adapter = new CircuitBreakerBillingAdapter(delegate, 2, 0L, 1, Duration.ofMinutes(1), Duration.ZERO);

            assertThatThrownBy(() -> adapter.pay(new UtilityPaymentRequestDto()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("pay failed");

            List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).anyMatch(message -> message.contains("billing_adapter_retry_failed"));
            assertThat(messages).anyMatch(message -> message.contains("billing_adapter_circuit_state_updated"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }
}