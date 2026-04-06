package com.elvo.identity.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(redisTemplate, "elvo:jwt:revoked:", "elvo:dev");
    }

    @Test
    void shouldStoreRevokedJtiUsingSharedNamespaceKey() {
        Instant expiresAt = Instant.now().plusSeconds(90);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.revokeJti("token-123", expiresAt);

        verify(valueOperations).set(
            eq("elvo:jwt:revoked:elvo:dev:token-123"),
            eq("1"),
            argThat(ttl -> ttl != null && ttl.compareTo(Duration.ZERO) > 0 && ttl.getSeconds() <= 90)
        );
    }

    @Test
    void shouldReadRevokedJtiUsingSharedNamespaceKey() {
        when(redisTemplate.hasKey("elvo:jwt:revoked:elvo:dev:token-123")).thenReturn(Boolean.TRUE);

        assertTrue(service.isRevoked("token-123"));
    }

    @Test
    void shouldIgnoreBlankJti() {
        assertFalse(service.isRevoked("  "));
        service.revokeJti("  ", Instant.now().plusSeconds(60));

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(valueOperations);
    }
}
