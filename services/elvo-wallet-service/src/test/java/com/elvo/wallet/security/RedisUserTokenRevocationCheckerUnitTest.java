package com.elvo.wallet.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisUserTokenRevocationCheckerUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisUserTokenRevocationChecker checker;

    @BeforeEach
    void setUp() {
        checker = new RedisUserTokenRevocationChecker(redisTemplate, "elvo:jwt:revoked:", "elvo:dev");
    }

    @Test
    void shouldReadRevocationUsingSharedNamespaceKey() {
        when(redisTemplate.hasKey("elvo:jwt:revoked:elvo:dev:token-123")).thenReturn(Boolean.TRUE);

        assertTrue(checker.isRevoked("token-123"));
        verify(redisTemplate).hasKey("elvo:jwt:revoked:elvo:dev:token-123");
    }

    @Test
    void shouldReturnFalseForBlankJtiWithoutRedisLookup() {
        assertFalse(checker.isRevoked("  "));
        verifyNoInteractions(redisTemplate);
    }
}
