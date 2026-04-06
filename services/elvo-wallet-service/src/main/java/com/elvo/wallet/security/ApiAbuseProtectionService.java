package com.elvo.wallet.security;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApiAbuseProtectionService {

    public record AbuseDecision(boolean allowed, String reason, Instant blockedUntil) {
        public static AbuseDecision allow() {
            return new AbuseDecision(true, null, null);
        }

        public static AbuseDecision block(String reason, Instant blockedUntil) {
            return new AbuseDecision(false, reason, blockedUntil);
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final int maxViolations;
    private final Duration violationWindow;
    private final Duration blockDuration;
    private final String keyPrefix;
    private final Map<String, LocalBlockState> fallbackState = new ConcurrentHashMap<>();

    public ApiAbuseProtectionService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${elvo.security.api-abuse.max-violations:5}") int maxViolations,
            @Value("${elvo.security.api-abuse.violation-window-seconds:600}") int violationWindowSeconds,
            @Value("${elvo.security.api-abuse.block-seconds:900}") int blockSeconds,
            @Value("${elvo.security.api-abuse.key-prefix:elvo:wallet:abuse:}") String keyPrefix) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.maxViolations = Math.max(1, maxViolations);
        this.violationWindow = Duration.ofSeconds(Math.max(30L, violationWindowSeconds));
        this.blockDuration = Duration.ofSeconds(Math.max(60L, blockSeconds));
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "elvo:wallet:abuse:" : keyPrefix;
    }

    public AbuseDecision evaluate(UUID userId, String sourceIp, String deviceId) {
        Map<String, String> dimensions = dimensions(userId, sourceIp, deviceId);
        for (Map.Entry<String, String> entry : dimensions.entrySet()) {
            String key = entry.getValue();
            if (key == null) {
                continue;
            }
            Instant blockedUntil = blockedUntil(key);
            if (blockedUntil != null && blockedUntil.isAfter(Instant.now())) {
                return AbuseDecision.block("API abuse block on " + entry.getKey(), blockedUntil);
            }
        }
        return AbuseDecision.allow();
    }

    public void recordViolation(UUID userId, String sourceIp, String deviceId) {
        Map<String, String> dimensions = dimensions(userId, sourceIp, deviceId);
        dimensions.values().stream().filter(value -> value != null).forEach(this::incrementViolation);
    }

    public void recordSuccess(UUID userId, String sourceIp, String deviceId) {
        Map<String, String> dimensions = dimensions(userId, sourceIp, deviceId);
        dimensions.values().stream().filter(value -> value != null).forEach(this::clearViolation);
    }

    private Map<String, String> dimensions(UUID userId, String sourceIp, String deviceId) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("user", userId == null ? null : keyPrefix + "user:" + userId);
        values.put("ip", sourceIp == null || sourceIp.isBlank() ? null : keyPrefix + "ip:" + sourceIp.trim().toLowerCase());
        values.put("device", deviceId == null || deviceId.isBlank() ? null : keyPrefix + "device:" + deviceId.trim().toLowerCase());
        return values;
    }

    private Instant blockedUntil(String key) {
        if (redisTemplate != null) {
            try {
                String value = redisTemplate.opsForValue().get(key + ":blocked");
                if (value != null && !value.isBlank()) {
                    return Instant.parse(value);
                }
            } catch (RuntimeException ignored) {
            }
        }

        LocalBlockState local = fallbackState.get(key);
        if (local == null) {
            return null;
        }
        return local.blockedUntil();
    }

    private void incrementViolation(String key) {
        if (redisTemplate != null) {
            try {
                Long violations = redisTemplate.opsForValue().increment(key + ":violations");
                if (violations != null && violations == 1L) {
                    redisTemplate.expire(key + ":violations", violationWindow);
                }
                if (violations != null && violations >= maxViolations) {
                    Instant blockedUntil = Instant.now().plus(blockDuration);
                    redisTemplate.opsForValue().set(key + ":blocked", blockedUntil.toString(), blockDuration);
                }
                return;
            } catch (RuntimeException ignored) {
            }
        }

        LocalBlockState state = fallbackState.computeIfAbsent(key, unused -> new LocalBlockState(0, Instant.now().plus(violationWindow), null));
        synchronized (state) {
            Instant now = Instant.now();
            if (state.windowEndsAt().isBefore(now)) {
                state.resetWindow(now.plus(violationWindow));
            }
            state.incrementViolations();
            if (state.violations() >= maxViolations) {
                state.blockUntil(now.plus(blockDuration));
            }
        }
    }

    private void clearViolation(String key) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key + ":violations");
                redisTemplate.delete(key + ":blocked");
                return;
            } catch (RuntimeException ignored) {
            }
        }
        fallbackState.remove(key);
    }

    private static final class LocalBlockState {
        private int violations;
        private Instant windowEndsAt;
        private Instant blockedUntil;

        private LocalBlockState(int violations, Instant windowEndsAt, Instant blockedUntil) {
            this.violations = violations;
            this.windowEndsAt = windowEndsAt;
            this.blockedUntil = blockedUntil;
        }

        int violations() {
            return violations;
        }

        Instant windowEndsAt() {
            return windowEndsAt;
        }

        Instant blockedUntil() {
            return blockedUntil;
        }

        void resetWindow(Instant nextWindowEnd) {
            this.violations = 0;
            this.windowEndsAt = nextWindowEnd;
            this.blockedUntil = null;
        }

        void incrementViolations() {
            this.violations += 1;
        }

        void blockUntil(Instant until) {
            this.blockedUntil = until;
        }
    }
}
