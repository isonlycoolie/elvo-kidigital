package com.elvo.billing.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
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
}