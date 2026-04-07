package com.elvo.billing.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;

public final class CircuitBreakerBillingAdapter implements BillingAdapter {

    private final BillingAdapter delegate;
    private final int maxAttempts;
    private final long baseBackoffMillis;
    private final int failureThreshold;
    private final Duration openDuration;

    private int consecutiveFailures;
    private Instant openUntil;

    public CircuitBreakerBillingAdapter(BillingAdapter delegate, int maxAttempts, long baseBackoffMillis, int failureThreshold, Duration openDuration) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMillis = Math.max(0L, baseBackoffMillis);
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = Objects.requireNonNull(openDuration, "openDuration must not be null");
    }

    @Override
    public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
        return executeWithRetry(() -> delegate.lookup(paymentRequest));
    }

    @Override
    public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
        return executeWithRetry(() -> delegate.pay(paymentRequest));
    }

    private synchronized <T> T executeWithRetry(Operation<T> operation) {
        ensureCircuitAllowsRequest();

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = operation.execute();
                consecutiveFailures = 0;
                openUntil = null;
                return result;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (attempt < maxAttempts) {
                    sleepBackoff(attempt);
                    continue;
                }

                registerFailure();
                throw lastFailure;
            }
        }

        throw lastFailure == null ? new IllegalStateException("adapter execution failed") : lastFailure;
    }

    private void ensureCircuitAllowsRequest() {
        if (openUntil == null) {
            return;
        }

        if (Instant.now().isBefore(openUntil)) {
            throw new IllegalStateException("circuit breaker is open");
        }

        consecutiveFailures = 0;
        openUntil = null;
    }

    private void registerFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openUntil = Instant.now().plus(openDuration);
        }
    }

    private void sleepBackoff(int attempt) {
        long delay = baseBackoffMillis * (1L << (attempt - 1));
        if (delay <= 0L) {
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("retry interrupted", ex);
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T execute();
    }
}