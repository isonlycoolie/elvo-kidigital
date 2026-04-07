package com.elvo.billing.statemachine;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BillingRetryMechanism {

    private final BillingStateTransitionHandlers stateTransitionHandlers;
    private final int maxAttempts;
    private final Duration circuitOpenDuration;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant circuitOpenedAt;

    public BillingRetryMechanism(
            BillingStateTransitionHandlers stateTransitionHandlers,
            @Value("${elvo.billing.retry.max-attempts:3}") int maxAttempts,
            @Value("${elvo.billing.retry.circuit-open-seconds:15}") long circuitOpenSeconds) {
        this.stateTransitionHandlers = stateTransitionHandlers;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.circuitOpenDuration = Duration.ofSeconds(Math.max(1L, circuitOpenSeconds));
    }

    public PaymentResponseDto executePaymentWithRetry(String serviceCode, UtilityPaymentRequestDto paymentRequest) {
        if (isCircuitOpen()) {
            PaymentResponseDto response = new PaymentResponseDto();
            response.setStatus(PaymentStatus.FAILED);
            response.setMessage("Circuit open for billing retry mechanism");
            return response;
        }

        RuntimeException lastError = null;
        PaymentResponseDto lastResponse = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                PaymentResponseDto response = stateTransitionHandlers.handleWalletCall(serviceCode, paymentRequest);
                if (response != null && response.getStatus() != PaymentStatus.FAILED) {
                    consecutiveFailures.set(0);
                    circuitOpenedAt = null;
                    return response;
                }
                lastResponse = response;
            } catch (RuntimeException ex) {
                lastError = ex;
            }
        }

        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= maxAttempts) {
            circuitOpenedAt = Instant.now();
        }

        if (lastError != null) {
            throw lastError;
        }

        if (lastResponse != null) {
            return lastResponse;
        }

        PaymentResponseDto response = new PaymentResponseDto();
        response.setStatus(PaymentStatus.FAILED);
        response.setMessage("Billing retry mechanism failed");
        return response;
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
}
