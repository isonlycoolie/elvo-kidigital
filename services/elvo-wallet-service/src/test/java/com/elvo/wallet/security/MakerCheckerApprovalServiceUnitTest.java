package com.elvo.wallet.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MakerCheckerApprovalServiceUnitTest {

    @SuppressWarnings("unchecked")
    private MakerCheckerApprovalService serviceWithoutRedis() {
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new MakerCheckerApprovalService(provider, true, new BigDecimal("1000.00"), new BigDecimal("750.00"), 3600);
    }

    @SuppressWarnings("unchecked")
    private MakerCheckerApprovalService serviceWithRedis(StringRedisTemplate redisTemplate) {
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        return new MakerCheckerApprovalService(provider, true, new BigDecimal("1000.00"), new BigDecimal("750.00"), 3600);
    }

    @Test
    void shouldReturnPendingForHighRiskAmountWithoutApprovalToken() {
        MakerCheckerApprovalService service = serviceWithoutRedis();

        MakerCheckerApprovalService.ApprovalDecision decision = service.evaluate(
                MakerCheckerApprovalService.Operation.TRANSFER,
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                null);

        assertFalse(decision.allowed());
        assertTrue(decision.pending());
    }

    @Test
    void shouldAllowWhenApprovedDecisionExists() {
        String approvalId = UUID.randomUUID().toString();
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("elvo:wallet:maker-checker:decision:" + approvalId)).thenReturn("APPROVED|ok");

        MakerCheckerApprovalService service = serviceWithRedis(redisTemplate);

        MakerCheckerApprovalService.ApprovalDecision decision = service.evaluate(
                MakerCheckerApprovalService.Operation.WITHDRAWAL,
                UUID.randomUUID(),
                new BigDecimal("800.00"),
                approvalId);

        assertTrue(decision.allowed());
        assertFalse(decision.pending());
    }
}
