package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisVerificationTokenServiceUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisVerificationTokenService service;

    @BeforeEach
    void setUp() {
        service = new RedisVerificationTokenService(redisTemplate, 10, "elvo:verification:token:", "elvo:verification:user:");
    }

    @Test
    void issueTokenShouldUseAtomicScript() {
        UUID userId = UUID.randomUUID();

        var token = service.issueToken(userId);

        assertNotNull(token.token());
        assertNotNull(token.expiresAt());
        verify(redisTemplate).execute(any(), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void invalidateShouldUseAtomicScript() {
        service.invalidateForUser(UUID.randomUUID());

        verify(redisTemplate).execute(any(), anyList());
    }

    @Test
    void isValidForUserShouldMatchMappedUserId() {
        UUID userId = UUID.randomUUID();
        String token = "verification-token";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("elvo:verification:token:" + token)).thenReturn(userId.toString());

        assertTrue(service.isValidForUser(token, userId));
        assertFalse(service.isValidForUser(token, UUID.randomUUID()));
    }
}
