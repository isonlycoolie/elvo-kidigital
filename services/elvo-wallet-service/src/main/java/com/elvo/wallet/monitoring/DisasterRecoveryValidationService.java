package com.elvo.wallet.monitoring;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DisasterRecoveryValidationService {

    public record ValidationReport(boolean ready, Map<String, Boolean> checks, String strategy, String primaryRegion, String secondaryRegion) {
    }

    private final String strategy;
    private final String primaryRegion;
    private final String secondaryRegion;
    private final long maxReplicationLagSeconds;
    private final long observedReplicationLagSeconds;

    public DisasterRecoveryValidationService(
            @Value("${elvo.resilience.dr.strategy:active-passive}") String strategy,
            @Value("${elvo.resilience.dr.primary-region:}") String primaryRegion,
            @Value("${elvo.resilience.dr.secondary-region:}") String secondaryRegion,
            @Value("${elvo.resilience.dr.max-replication-lag-seconds:60}") long maxReplicationLagSeconds,
            @Value("${elvo.resilience.dr.observed-replication-lag-seconds:0}") long observedReplicationLagSeconds) {
        this.strategy = strategy == null ? "" : strategy.trim().toLowerCase();
        this.primaryRegion = normalize(primaryRegion);
        this.secondaryRegion = normalize(secondaryRegion);
        this.maxReplicationLagSeconds = Math.max(1, maxReplicationLagSeconds);
        this.observedReplicationLagSeconds = Math.max(0, observedReplicationLagSeconds);
    }

    public ValidationReport validateReadiness() {
        Map<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("strategy_supported", "active-passive".equals(strategy) || "active-active".equals(strategy));
        checks.put("primary_region_configured", primaryRegion != null);
        checks.put("secondary_region_configured", secondaryRegion != null);
        checks.put("regions_are_distinct", primaryRegion != null && secondaryRegion != null && !primaryRegion.equals(secondaryRegion));
        checks.put("replication_lag_within_threshold", observedReplicationLagSeconds <= maxReplicationLagSeconds);

        boolean ready = checks.values().stream().allMatch(Boolean::booleanValue);
        return new ValidationReport(ready, checks, strategy, primaryRegion, secondaryRegion);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }
}
