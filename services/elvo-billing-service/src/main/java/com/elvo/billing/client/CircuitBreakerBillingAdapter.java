package com.elvo.billing.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;

public final class CircuitBreakerBillingAdapter implements BillingAdapter {

    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.adapter");
    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "billing-adapter-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private static final Clock CLOCK = Clock.systemUTC();

    private final BillingAdapter delegate;
    private final int maxAttempts;
    private final long baseBackoffMillis;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Duration requestTimeout;

    private int consecutiveFailures;
    private Instant openUntil;

    public CircuitBreakerBillingAdapter(BillingAdapter delegate, int maxAttempts, long baseBackoffMillis, int failureThreshold, Duration openDuration) {
        this(delegate, maxAttempts, baseBackoffMillis, failureThreshold, openDuration, Duration.ofSeconds(5));
    }

    public CircuitBreakerBillingAdapter(BillingAdapter delegate, int maxAttempts, long baseBackoffMillis, int failureThreshold, Duration openDuration, Duration requestTimeout) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMillis = Math.max(0L, baseBackoffMillis);
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = Objects.requireNonNull(openDuration, "openDuration must not be null");
        this.requestTimeout = requestTimeout == null || requestTimeout.isNegative() ? Duration.ZERO : requestTimeout;
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
                T result = executeWithTimeout(operation);
                consecutiveFailures = 0;
                openUntil = null;
                return result;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                auditLog.warn(
                        "billing_adapter_retry_failed attempt={} maxAttempts={} circuitOpenUntil={} error={}",
                        attempt,
                        maxAttempts,
                        openUntil,
                        ex.getMessage());
                if (attempt < maxAttempts) {
                    auditLog.info(
                            "billing_adapter_retry_scheduled attempt={} delayMillis={} adapter={}",
                            attempt,
                            computeBackoffMillis(attempt),
                            delegate.getClass().getSimpleName());
                    sleepBackoff(attempt);
                    continue;
                }

                registerFailure();
                auditLog.error(
                        "billing_adapter_circuit_state_updated consecutiveFailures={} failureThreshold={} openUntil={}",
                        consecutiveFailures,
                        failureThreshold,
                        openUntil);
                throw lastFailure;
            }
        }

        throw lastFailure == null ? new IllegalStateException("adapter execution failed") : lastFailure;
    }

    private void ensureCircuitAllowsRequest() {
        if (openUntil == null) {
            return;
        }

        if (CLOCK.instant().isBefore(openUntil)) {
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

    private <T> T executeWithTimeout(Operation<T> operation) {
        if (requestTimeout.isZero()) {
            return operation.execute();
        }

        Future<T> future = TIMEOUT_EXECUTOR.submit(operation::execute);
        try {
            return future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IllegalStateException("adapter execution timed out after " + requestTimeout.toMillis() + " ms", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("adapter execution interrupted", ex);
        } catch (Exception ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("adapter execution failed", ex);
        }
    }

    private long computeBackoffMillis(int attempt) {
        return baseBackoffMillis * (1L << (attempt - 1));
    }

    private void sleepBackoff(int attempt) {
        long delay = computeBackoffMillis(attempt);
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