package com.elvo.billing.security;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.monitoring.SecurityMonitoringService;

@Service
public class BillingFraudDetectionService {

    private final int frequencyWindowSeconds;
    private final int highFrequencyThreshold;
    private final int failedCallbackThreshold;
    private final SecurityMonitoringService securityMonitoringService;

    private final ConcurrentHashMap<String, SlidingCounter> requestFrequencyCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlidingCounter> failedCallbackCounters = new ConcurrentHashMap<>();

    public BillingFraudDetectionService(
            @Value("${elvo.security.fraud.frequency-window-seconds:300}") int frequencyWindowSeconds,
            @Value("${elvo.security.fraud.high-frequency-threshold:8}") int highFrequencyThreshold,
            @Value("${elvo.security.fraud.failed-callback-threshold:3}") int failedCallbackThreshold,
            @Nullable SecurityMonitoringService securityMonitoringService) {
        this.frequencyWindowSeconds = Math.max(30, frequencyWindowSeconds);
        this.highFrequencyThreshold = Math.max(2, highFrequencyThreshold);
        this.failedCallbackThreshold = Math.max(2, failedCallbackThreshold);
        this.securityMonitoringService = securityMonitoringService;
    }

    public void analyzePaymentAttempt(UtilityPaymentRequestDto request) {
        if (request == null) {
            return;
        }

        String key = normalized(request.getCustomerPhone()) + "|" + normalized(request.getReferenceNumber());
        int attempts = increment(requestFrequencyCounters, key, frequencyWindowSeconds);

        if (attempts >= highFrequencyThreshold) {
            emit("billing.fraud.high_frequency_payment_attempt", Map.of(
                    "customerPhone", SensitiveDataMasker.maskIdentifier(request.getCustomerPhone()),
                    "referenceNumber", SensitiveDataMasker.maskIdentifier(request.getReferenceNumber()),
                    "attempts", attempts,
                    "amount", normalizeAmount(request.getAmount())));
        }
    }

    public void recordCallbackOutcome(String referenceNumber, String callbackStatus) {
        String normalizedStatus = normalized(callbackStatus).toUpperCase();
        if (!"FAILED".equals(normalizedStatus)) {
            return;
        }

        int failures = increment(failedCallbackCounters, normalized(referenceNumber), frequencyWindowSeconds);
        if (failures >= failedCallbackThreshold) {
            emit("billing.fraud.repeated_failed_callback", Map.of(
                    "referenceNumber", SensitiveDataMasker.maskIdentifier(referenceNumber),
                    "failures", failures,
                    "status", normalizedStatus));
        }
    }

    private void emit(String eventType, Map<String, Object> details) {
        if (securityMonitoringService != null) {
            securityMonitoringService.recordSuspiciousEvent(eventType, "fraud_signal", details);
        }
    }

    private int increment(ConcurrentHashMap<String, SlidingCounter> counters, String key, int windowSeconds) {
        String safeKey = key == null || key.isBlank() ? "unknown" : key;
        Instant now = Instant.now();
        SlidingCounter counter = counters.computeIfAbsent(safeKey, ignored -> new SlidingCounter(now.plusSeconds(windowSeconds), 0));
        synchronized (counter) {
            if (now.isAfter(counter.windowEnd)) {
                counter.windowEnd = now.plusSeconds(windowSeconds);
                counter.value = 0;
            }
            counter.value += 1;
            return counter.value;
        }
    }

    private String normalized(String value) {
        return value == null ? "unknown" : value.trim();
    }

    private String normalizeAmount(BigDecimal amount) {
        return amount == null ? "unknown" : amount.toPlainString();
    }

    private static final class SlidingCounter {
        private Instant windowEnd;
        private int value;

        private SlidingCounter(Instant windowEnd, int value) {
            this.windowEnd = windowEnd;
            this.value = value;
        }
    }
}
