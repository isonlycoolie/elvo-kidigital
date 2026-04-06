package com.elvo.wallet.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class FraudRulesEngineUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @SuppressWarnings("unchecked")
    private FraudRulesEngine engineWithRedis() {
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return new FraudRulesEngine(provider, new BigDecimal("500.00"), new BigDecimal("250.00"));
    }

    @SuppressWarnings("unchecked")
    private FraudRulesEngine engineWithoutRedis() {
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new FraudRulesEngine(provider, new BigDecimal("500.00"), new BigDecimal("250.00"));
    }

    @Test
    void shouldRequireVerificationWhenAmountCrossesThreshold() {
        FraudRulesEngine engine = engineWithoutRedis();

        FraudRulesEngine.FraudDecision decision = engine.evaluate(
                FraudRulesEngine.Operation.TRANSFER,
                UUID.randomUUID(),
                new BigDecimal("500.00"),
                "target-1");

        assertFalse(decision.blocked());
        assertTrue(decision.requiresVerification());
    }

    @Test
    void shouldBlockWhenOperatorSetsBlockOverride() {
        FraudRulesEngine engine = engineWithRedis();
        UUID userId = UUID.randomUUID();
        when(valueOperations.get("elvo:wallet:fraud:override:user:" + userId)).thenReturn("BLOCK");

        FraudRulesEngine.FraudDecision decision = engine.evaluate(
                FraudRulesEngine.Operation.WITHDRAWAL,
                userId,
                new BigDecimal("10.00"),
                "+1234567890");

        assertTrue(decision.blocked());
        assertTrue(decision.requiresVerification());
    }

    @Test
    void shouldAllowWhenOperatorSetsAllowOverride() {
        FraudRulesEngine engine = engineWithRedis();
        UUID userId = UUID.randomUUID();
        when(valueOperations.get("elvo:wallet:fraud:override:user:" + userId)).thenReturn("ALLOW");

        FraudRulesEngine.FraudDecision decision = engine.evaluate(
                FraudRulesEngine.Operation.WITHDRAWAL,
                userId,
                new BigDecimal("1000.00"),
                "+1234567890");

        assertFalse(decision.blocked());
        assertFalse(decision.requiresVerification());
    }
}
