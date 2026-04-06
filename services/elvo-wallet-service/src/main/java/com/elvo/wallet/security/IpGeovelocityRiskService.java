package com.elvo.wallet.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IpGeovelocityRiskService {

    public record RiskDecision(boolean blocked, boolean requiresVerification, String reason) {
    }

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.risk.ip-geovelocity");

    private final Set<String> blockedIps;
    private final long rapidLocationChangeSeconds;
    private final Map<UUID, Context> lastSeenContextByUser = new ConcurrentHashMap<>();

    public IpGeovelocityRiskService(
            @Value("${elvo.security.ip-risk.blocked-ips:}") Set<String> blockedIps,
            @Value("${elvo.security.ip-risk.rapid-location-change-seconds:1800}") long rapidLocationChangeSeconds) {
        this.blockedIps = blockedIps == null ? Set.of() : blockedIps;
        this.rapidLocationChangeSeconds = Math.max(60, rapidLocationChangeSeconds);
    }

    public RiskDecision evaluate(UUID userId, String ipAddress, String locationHint) {
        if (userId == null) {
            return new RiskDecision(false, false, null);
        }

        String normalizedIp = normalize(ipAddress);
        String normalizedLocation = normalize(locationHint);

        if (normalizedIp != null && blockedIps.contains(normalizedIp)) {
            AUDIT_LOG.warn("wallet_ip_reputation_blocked userId={} ipAddress={}", userId, normalizedIp);
            return new RiskDecision(true, true, "IP reputation policy blocked this request");
        }

        Instant now = Instant.now();
        Context current = new Context(normalizedIp, normalizedLocation, now);
        Context previous = lastSeenContextByUser.putIfAbsent(userId, current);
        if (previous == null) {
            return new RiskDecision(false, false, null);
        }

        boolean locationChanged = changed(previous.locationHint(), normalizedLocation);
        long secondsSincePrevious = Math.max(0, Duration.between(previous.seenAt(), now).getSeconds());
        if (locationChanged && secondsSincePrevious < rapidLocationChangeSeconds) {
            AUDIT_LOG.warn(
                    "wallet_impossible_travel_detected userId={} previousLocation={} currentLocation={} elapsedSeconds={} minExpectedSeconds={}",
                    userId,
                    previous.locationHint(),
                    normalizedLocation,
                    secondsSincePrevious,
                    rapidLocationChangeSeconds);
            return new RiskDecision(false, true, "Location anomaly detected; additional verification required");
        }

        if (changed(previous.ipAddress(), normalizedIp)) {
            AUDIT_LOG.warn("wallet_new_ip_detected userId={} previousIp={} currentIp={}", userId, previous.ipAddress(), normalizedIp);
            lastSeenContextByUser.put(userId, current);
            return new RiskDecision(false, true, "New IP detected; additional verification required");
        }

        lastSeenContextByUser.put(userId, current);
        return new RiskDecision(false, false, null);
    }

    public void markTrusted(UUID userId, String ipAddress, String locationHint) {
        if (userId == null) {
            return;
        }
        lastSeenContextByUser.put(userId, new Context(normalize(ipAddress), normalize(locationHint), Instant.now()));
    }

    private boolean changed(String previous, String current) {
        if (previous == null || current == null) {
            return false;
        }
        return !previous.equals(current);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private record Context(String ipAddress, String locationHint, Instant seenAt) {
    }
}
