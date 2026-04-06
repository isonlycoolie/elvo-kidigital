package com.elvo.wallet.security;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FraudRulesEngine {

    public enum Operation {
        TRANSFER,
        WITHDRAWAL
    }

    public record FraudDecision(boolean blocked, boolean requiresVerification, String reason) {
        public static FraudDecision allow() {
            return new FraudDecision(false, false, null);
        }

        public static FraudDecision block(String reason) {
            return new FraudDecision(true, true, reason);
        }

        public static FraudDecision verify(String reason) {
            return new FraudDecision(false, true, reason);
        }
    }

    private static final String USER_OVERRIDE_PREFIX = "elvo:wallet:fraud:override:user:";
    private static final String TARGET_OVERRIDE_PREFIX = "elvo:wallet:fraud:override:target:";

    private final StringRedisTemplate redisTemplate;
    private final BigDecimal transferVerificationThreshold;
    private final BigDecimal withdrawalVerificationThreshold;

    public FraudRulesEngine(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${elvo.security.fraud.rules.transfer-verification-threshold:500.00}") BigDecimal transferVerificationThreshold,
            @Value("${elvo.security.fraud.rules.withdrawal-verification-threshold:250.00}") BigDecimal withdrawalVerificationThreshold) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.transferVerificationThreshold = transferVerificationThreshold == null
                ? new BigDecimal("500.00") : transferVerificationThreshold;
        this.withdrawalVerificationThreshold = withdrawalVerificationThreshold == null
                ? new BigDecimal("250.00") : withdrawalVerificationThreshold;
    }

    public FraudDecision evaluate(Operation operation, UUID userId, BigDecimal amount, String targetIdentifier) {
        if (operation == null || userId == null || amount == null) {
            return FraudDecision.allow();
        }

        FraudDecision userOverrideDecision = evaluateOverride(readOverride(USER_OVERRIDE_PREFIX + userId));
        if (userOverrideDecision != null) {
            return userOverrideDecision;
        }

        String normalizedTarget = normalize(targetIdentifier);
        if (normalizedTarget != null) {
            FraudDecision targetOverrideDecision = evaluateOverride(readOverride(TARGET_OVERRIDE_PREFIX + normalizedTarget));
            if (targetOverrideDecision != null) {
                return targetOverrideDecision;
            }
        }

        BigDecimal threshold = operation == Operation.TRANSFER
                ? transferVerificationThreshold
                : withdrawalVerificationThreshold;

        if (amount.compareTo(threshold) >= 0) {
            return FraudDecision.verify("Fraud rules require additional verification");
        }

        return FraudDecision.allow();
    }

    private String readOverride(String key) {
        if (redisTemplate == null || key == null || key.isBlank()) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private FraudDecision evaluateOverride(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BLOCK" -> FraudDecision.block("Transaction blocked by operator fraud override");
            case "STEP_UP", "VERIFY" -> FraudDecision.verify("Operator fraud override requires additional verification");
            case "ALLOW" -> FraudDecision.allow();
            default -> null;
        };
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
