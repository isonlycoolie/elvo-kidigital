package com.elvo.wallet.security;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeviceLocationRiskService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.risk.device-location");

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final int riskThreshold;
    private final int trustTtlDays;
    private final int maxTrustScore;

    public DeviceLocationRiskService(StringRedisTemplate redisTemplate,
                                     @Value("${elvo.security.device-trust.key-prefix:elvo:wallet:device-trust:}") String keyPrefix,
                                     @Value("${elvo.security.device-trust.risk-threshold:60}") int riskThreshold,
                                     @Value("${elvo.security.device-trust.ttl-days:30}") int trustTtlDays,
                                     @Value("${elvo.security.device-trust.max-score:100}") int maxTrustScore) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.riskThreshold = Math.max(1, riskThreshold);
        this.trustTtlDays = Math.max(1, trustTtlDays);
        this.maxTrustScore = Math.max(10, maxTrustScore);
    }

    public boolean requiresAdditionalVerification(UUID userId, String deviceId, String locationHint) {
        if (userId == null) {
            return false;
        }

        String key = key(userId);
        String normalizedDevice = normalize(deviceId);
        String normalizedLocation = normalize(locationHint);
        Instant now = Instant.now();
        ContextSnapshot current = new ContextSnapshot(normalizedDevice, normalizedLocation, now, 0);

        Map<Object, Object> state = redisTemplate.opsForHash().entries(key);
        if (state == null || state.isEmpty()) {
            persistContext(key, current, 0);
            return false;
        }

        ContextSnapshot previous = fromState(state);
        int updatedTrustScore = computeTrustScore(previous, current);
        boolean risky = updatedTrustScore >= riskThreshold;
        persistContext(key, current, updatedTrustScore);

        if (risky) {
            AUDIT_LOG.warn("wallet_device_location_risk userId={} previousDevice={} currentDevice={} previousLocation={} currentLocation={} lastSeen={} trustScore={} threshold={}",
                    userId,
                    previous.deviceId(),
                    normalizedDevice,
                    previous.locationHint(),
                    normalizedLocation,
                    previous.seenAt(),
                    updatedTrustScore,
                    riskThreshold);
            return true;
        }
        return false;
    }

    public void markTrusted(UUID userId, String deviceId, String locationHint) {
        if (userId == null) {
            return;
        }
        persistContext(
                key(userId),
                new ContextSnapshot(normalize(deviceId), normalize(locationHint), Instant.now(), 0),
                0);
    }

    private int computeTrustScore(ContextSnapshot previous, ContextSnapshot current) {
        int riskPoints = 0;
        boolean deviceChanged = changed(previous.deviceId(), current.deviceId());
        boolean locationChanged = changed(previous.locationHint(), current.locationHint());

        if (deviceChanged) {
            riskPoints += 70;
        }
        if (locationChanged) {
            riskPoints += 40;
        }
        if (locationChanged && previous.seenAt() != null && previous.seenAt().isAfter(Instant.now().minusSeconds(300))) {
            riskPoints += 20;
        }

        if (riskPoints == 0) {
            return Math.max(0, previous.trustScore() - 20);
        }

        return Math.min(maxTrustScore, previous.trustScore() + riskPoints);
    }

    private boolean changed(String previous, String current) {
        if (previous == null || current == null) {
            return false;
        }
        return !previous.equals(current);
    }

    private ContextSnapshot fromState(Map<Object, Object> state) {
        return new ContextSnapshot(
                normalize(asString(state.get("deviceId"))),
                normalize(asString(state.get("locationHint"))),
                asInstant(state.get("seenAt")),
                asInt(state.get("trustScore")));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Instant asInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void persistContext(String key, ContextSnapshot context, int trustScore) {
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        hashOperations.put(key, "deviceId", context.deviceId() == null ? "" : context.deviceId());
        hashOperations.put(key, "locationHint", context.locationHint() == null ? "" : context.locationHint());
        hashOperations.put(key, "seenAt", String.valueOf(context.seenAt().getEpochSecond()));
        hashOperations.put(key, "trustScore", String.valueOf(Math.max(0, Math.min(maxTrustScore, trustScore))));
        redisTemplate.expire(key, Duration.ofDays(trustTtlDays));
    }

    private String key(UUID userId) {
        return keyPrefix + userId;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ContextSnapshot(String deviceId, String locationHint, Instant seenAt, int trustScore) {
    }
}