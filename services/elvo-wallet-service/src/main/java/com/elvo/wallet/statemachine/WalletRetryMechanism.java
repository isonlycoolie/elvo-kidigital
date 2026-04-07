package com.elvo.wallet.statemachine;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletRetryMechanism {

    private final WalletStateTransitionHandlers stateTransitionHandlers;
    private final int maxAttempts;
    private final Duration circuitOpenDuration;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant circuitOpenedAt;

    public WalletRetryMechanism(WalletStateTransitionHandlers stateTransitionHandlers,
                                @Value("${elvo.wallet.retry.max-attempts:3}") int maxAttempts,
                                @Value("${elvo.wallet.retry.circuit-open-seconds:15}") long circuitOpenSeconds) {
        this.stateTransitionHandlers = stateTransitionHandlers;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.circuitOpenDuration = Duration.ofSeconds(Math.max(1L, circuitOpenSeconds));
    }

    public WalletFlowResult reserveWithRetry(UUID walletId,
                                             UUID userId,
                                             BigDecimal amount,
                                             String idempotencyKey,
                                             String reference) {
        return executeWithRetry(() -> stateTransitionHandlers.reserveFunds(walletId, userId, amount, idempotencyKey, reference),
                walletId,
                "wallet.transaction.failed");
    }

    public WalletFlowResult commitWithRetry(UUID reservationId, String idempotencyKey) {
        return executeWithRetry(() -> stateTransitionHandlers.commitFunds(reservationId, idempotencyKey),
                reservationId,
                "wallet.transaction.failed");
    }

    public WalletFlowResult rollbackWithRetry(UUID reservationId, String idempotencyKey) {
        return executeWithRetry(() -> stateTransitionHandlers.rollbackFunds(reservationId, idempotencyKey),
                reservationId,
                "wallet.transaction.failed");
    }

    private WalletFlowResult executeWithRetry(Action action, UUID walletId, String fallbackEventType) {
        if (isCircuitOpen()) {
            return WalletFlowResult.failure("Circuit open for wallet retry mechanism", walletId, fallbackEventType);
        }

        WalletFlowResult lastResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            lastResult = action.execute();
            if (lastResult.success()) {
                consecutiveFailures.set(0);
                circuitOpenedAt = null;
                return lastResult;
            }
        }

        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= maxAttempts) {
            circuitOpenedAt = Instant.now();
        }
        return lastResult == null
                ? WalletFlowResult.failure("Wallet retry mechanism failed", walletId, fallbackEventType)
                : lastResult;
    }

    private boolean isCircuitOpen() {
        Instant openedAt = circuitOpenedAt;
        if (openedAt == null) {
            return false;
        }
        if (Instant.now().isAfter(openedAt.plus(circuitOpenDuration))) {
            circuitOpenedAt = null;
            consecutiveFailures.set(0);
            return false;
        }
        return true;
    }

    @FunctionalInterface
    private interface Action {
        WalletFlowResult execute();
    }
}
