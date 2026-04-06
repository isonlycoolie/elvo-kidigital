package com.elvo.wallet.security;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DestinationRiskService {

    public record DestinationRiskDecision(boolean blocked, boolean requiresVerification, String reason) {
    }

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.risk.destination");

    private final BigDecimal unusualAmountThreshold;
    private final int maxFailuresBeforeBlock;
    private final int failureWindowSeconds;
    private final Map<UUID, Set<String>> knownDestinations = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> failureHistory = new ConcurrentHashMap<>();

    public DestinationRiskService(
            @org.springframework.beans.factory.annotation.Value("${elvo.security.destination-risk.unusual-amount-threshold:500.00}") BigDecimal unusualAmountThreshold,
            @org.springframework.beans.factory.annotation.Value("${elvo.security.destination-risk.max-failures-before-block:3}") int maxFailuresBeforeBlock,
            @org.springframework.beans.factory.annotation.Value("${elvo.security.destination-risk.failure-window-seconds:900}") int failureWindowSeconds) {
        this.unusualAmountThreshold = unusualAmountThreshold == null ? new BigDecimal("500.00") : unusualAmountThreshold;
        this.maxFailuresBeforeBlock = Math.max(2, maxFailuresBeforeBlock);
        this.failureWindowSeconds = Math.max(60, failureWindowSeconds);
    }

    public DestinationRiskDecision evaluate(UUID userId, String destinationNumber, BigDecimal amount) {
        if (userId == null || destinationNumber == null || destinationNumber.isBlank() || amount == null) {
            return new DestinationRiskDecision(false, false, null);
        }

        String normalizedDestination = destinationNumber.trim();
        String failureKey = failureKey(userId, normalizedDestination);
        if (recentFailureCount(failureKey) >= maxFailuresBeforeBlock) {
            AUDIT_LOG.warn("wallet_destination_risk_blocked userId={} destination={} reason=repeated_failed_attempts", userId, normalizedDestination);
            return new DestinationRiskDecision(true, true, "Destination temporarily blocked due to repeated failed attempts");
        }

        boolean firstTime = knownDestinations.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .contains(normalizedDestination) == false;
        boolean unusualAmount = amount.compareTo(unusualAmountThreshold) > 0;
        boolean requiresVerification = firstTime || unusualAmount;

        if (requiresVerification) {
            AUDIT_LOG.warn("wallet_destination_risk_challenge userId={} destination={} firstTime={} unusualAmount={} amount={}",
                    userId, normalizedDestination, firstTime, unusualAmount, amount);
            return new DestinationRiskDecision(false, true, "Additional destination verification required");
        }

        return new DestinationRiskDecision(false, false, null);
    }

    public void recordFailure(UUID userId, String destinationNumber) {
        if (userId == null || destinationNumber == null || destinationNumber.isBlank()) {
            return;
        }
        String key = failureKey(userId, destinationNumber.trim());
        Deque<Instant> failures = failureHistory.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (failures) {
            prune(failures);
            failures.addLast(Instant.now());
        }
    }

    public void markTrusted(UUID userId, String destinationNumber) {
        if (userId == null || destinationNumber == null || destinationNumber.isBlank()) {
            return;
        }
        String normalized = destinationNumber.trim();
        knownDestinations.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(normalized);
        failureHistory.remove(failureKey(userId, normalized));
    }

    private int recentFailureCount(String key) {
        Deque<Instant> failures = failureHistory.get(key);
        if (failures == null) {
            return 0;
        }
        synchronized (failures) {
            prune(failures);
            return failures.size();
        }
    }

    private void prune(Deque<Instant> failures) {
        Instant lowerBound = Instant.now().minusSeconds(failureWindowSeconds);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(lowerBound)) {
            failures.removeFirst();
        }
    }

    private String failureKey(UUID userId, String destination) {
        return userId + ":" + destination;
    }
}