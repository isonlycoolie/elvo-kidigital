package com.elvo.wallet.security;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MakerCheckerApprovalService {

    public enum Operation {
        TRANSFER,
        WITHDRAWAL
    }

    public record ApprovalDecision(boolean allowed, boolean pending, boolean rejected, String approvalId, String reason) {
        public static ApprovalDecision allow() {
            return new ApprovalDecision(true, false, false, null, null);
        }

        public static ApprovalDecision pending(String approvalId, String reason) {
            return new ApprovalDecision(false, true, false, approvalId, reason);
        }

        public static ApprovalDecision reject(String reason) {
            return new ApprovalDecision(false, false, true, null, reason);
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final BigDecimal transferThreshold;
    private final BigDecimal withdrawalThreshold;
    private final long ttlSeconds;
    private final Map<String, String> fallbackDecisions = new ConcurrentHashMap<>();

    public MakerCheckerApprovalService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${elvo.security.maker-checker.enabled:true}") boolean enabled,
            @Value("${elvo.security.maker-checker.transfer-threshold:1000.00}") BigDecimal transferThreshold,
            @Value("${elvo.security.maker-checker.withdrawal-threshold:750.00}") BigDecimal withdrawalThreshold,
            @Value("${elvo.security.maker-checker.approval-ttl-seconds:86400}") long ttlSeconds) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.enabled = enabled;
        this.transferThreshold = transferThreshold == null ? new BigDecimal("1000.00") : transferThreshold;
        this.withdrawalThreshold = withdrawalThreshold == null ? new BigDecimal("750.00") : withdrawalThreshold;
        this.ttlSeconds = Math.max(300L, ttlSeconds);
    }

    public ApprovalDecision evaluate(Operation operation, UUID userId, BigDecimal amount, String approvalToken) {
        if (!enabled || operation == null || userId == null || amount == null) {
            return ApprovalDecision.allow();
        }

        BigDecimal threshold = operation == Operation.TRANSFER ? transferThreshold : withdrawalThreshold;
        if (amount.compareTo(threshold) < 0) {
            return ApprovalDecision.allow();
        }

        if (approvalToken != null && !approvalToken.isBlank()) {
            String status = readDecision(approvalToken.trim());
            if ("APPROVED".equalsIgnoreCase(status)) {
                return ApprovalDecision.allow();
            }
            if ("REJECTED".equalsIgnoreCase(status)) {
                return ApprovalDecision.reject("Transaction rejected by maker-checker workflow");
            }
        }

        String approvalId = UUID.randomUUID().toString();
        persistPendingApproval(approvalId, operation, userId, amount);
        return ApprovalDecision.pending(approvalId, "Maker-checker approval required for high-risk transaction");
    }

    public void recordDecision(String approvalId, boolean approved, String reason) {
        if (approvalId == null || approvalId.isBlank()) {
            return;
        }
        String decision = approved ? "APPROVED" : "REJECTED";
        String key = decisionKey(approvalId);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, decision + "|" + safe(reason), java.time.Duration.ofSeconds(ttlSeconds));
                return;
            } catch (RuntimeException ignored) {
            }
        }
        fallbackDecisions.put(approvalId, decision + "|" + safe(reason));
    }

    private void persistPendingApproval(String approvalId, Operation operation, UUID userId, BigDecimal amount) {
        if (redisTemplate != null) {
            try {
                Map<String, String> data = Map.of(
                        "status", "PENDING",
                        "operation", operation.name(),
                        "userId", userId.toString(),
                        "amount", amount.toPlainString(),
                        "createdAt", Instant.now().toString());
                String key = pendingKey(approvalId);
                redisTemplate.opsForHash().putAll(key, data);
                redisTemplate.expire(key, java.time.Duration.ofSeconds(ttlSeconds));
                return;
            } catch (RuntimeException ignored) {
            }
        }
        fallbackDecisions.put(approvalId, "PENDING");
    }

    private String readDecision(String approvalId) {
        String key = decisionKey(approvalId);
        if (redisTemplate != null) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    return value.split("\\|", 2)[0];
                }
            } catch (RuntimeException ignored) {
            }
        }
        String value = fallbackDecisions.get(approvalId);
        if (value == null) {
            return null;
        }
        return value.split("\\|", 2)[0];
    }

    private String pendingKey(String approvalId) {
        return "elvo:wallet:maker-checker:pending:" + approvalId;
    }

    private String decisionKey(String approvalId) {
        return "elvo:wallet:maker-checker:decision:" + approvalId;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
