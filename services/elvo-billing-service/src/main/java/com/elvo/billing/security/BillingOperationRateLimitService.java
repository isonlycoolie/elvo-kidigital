package com.elvo.billing.security;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class BillingOperationRateLimitService {

    public enum Operation {
        CREATE_PAYMENT,
        LOOKUP_PAYMENT,
        PROVIDER_CALLBACK,
        WALLET_EVENT_CONSUME
    }

    public record RateLimitResult(boolean allowed, String reason) {
        public static RateLimitResult allow() {
            return new RateLimitResult(true, null);
        }

        public static RateLimitResult deny(String reason) {
            return new RateLimitResult(false, reason);
        }
    }

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.billing.rate-limit");

    private final StringRedisTemplate redisTemplate;
    private final int windowSeconds;
    private final int perPrimaryKeyLimit;
    private final int perSecondaryKeyLimit;
    private final int perOperationLimit;
    private final Map<String, LocalCounter> fallbackCounters = new ConcurrentHashMap<>();

    public BillingOperationRateLimitService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${elvo.security.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${elvo.security.rate-limit.per-primary-threshold:40}") int perPrimaryKeyLimit,
            @Value("${elvo.security.rate-limit.per-secondary-threshold:25}") int perSecondaryKeyLimit,
            @Value("${elvo.security.rate-limit.per-operation-threshold:200}") int perOperationLimit) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.windowSeconds = Math.max(10, windowSeconds);
        this.perPrimaryKeyLimit = Math.max(1, perPrimaryKeyLimit);
        this.perSecondaryKeyLimit = Math.max(1, perSecondaryKeyLimit);
        this.perOperationLimit = Math.max(10, perOperationLimit);
    }

    public RateLimitResult enforce(Operation operation, String primaryKey, String secondaryKey) {
        Map<String, Integer> checks = new LinkedHashMap<>();
        checks.put(dimensionKey(operation, "operation", operation.name()), perOperationLimit);
        checks.put(dimensionKey(operation, "primary", primaryKey), perPrimaryKeyLimit);
        checks.put(dimensionKey(operation, "secondary", secondaryKey), perSecondaryKeyLimit);

        for (Map.Entry<String, Integer> check : checks.entrySet()) {
            String key = check.getKey();
            if (key == null) {
                continue;
            }
            long current = increment(key);
            if (current > check.getValue()) {
                AUDIT_LOG.warn("billing_operation_rate_limited operation={} key={} count={} threshold={}",
                        operation, key, current, check.getValue());
                return RateLimitResult.deny("Rate limit exceeded");
            }
        }

        return RateLimitResult.allow();
    }

    private long increment(String dimensionKey) {
        if (redisTemplate != null) {
            try {
                Long value = redisTemplate.opsForValue().increment(dimensionKey);
                if (value != null && value == 1L) {
                    redisTemplate.expire(dimensionKey, java.time.Duration.ofSeconds(windowSeconds + 5L));
                }
                if (value != null) {
                    return value;
                }
            } catch (RuntimeException ex) {
                AUDIT_LOG.warn("billing_rate_limit_redis_unavailable reason={}", SensitiveDataMasker.maskText(ex.getMessage()));
            }
        }

        Instant now = Instant.now();
        LocalCounter counter = fallbackCounters.computeIfAbsent(dimensionKey,
                ignored -> new LocalCounter(now.plusSeconds(windowSeconds), 0));
        synchronized (counter) {
            if (now.isAfter(counter.windowEnd())) {
                counter.reset(now.plusSeconds(windowSeconds));
            }
            return counter.increment();
        }
    }

    private String dimensionKey(Operation operation, String dimension, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        long bucket = Instant.now().getEpochSecond() / windowSeconds;
        return "billing:rate:" + operation + ":" + dimension + ":" + value.trim().toLowerCase() + ":" + bucket;
    }

    private static final class LocalCounter {
        private Instant windowEnd;
        private long value;

        private LocalCounter(Instant windowEnd, long value) {
            this.windowEnd = windowEnd;
            this.value = value;
        }

        Instant windowEnd() {
            return windowEnd;
        }

        void reset(Instant newWindowEnd) {
            this.windowEnd = newWindowEnd;
            this.value = 0;
        }

        long increment() {
            value += 1;
            return value;
        }
    }
}
