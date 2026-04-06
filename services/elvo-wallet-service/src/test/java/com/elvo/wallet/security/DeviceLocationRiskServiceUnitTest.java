package com.elvo.wallet.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class DeviceLocationRiskServiceUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private DeviceLocationRiskService service;

    @BeforeEach
    void setUp() {
        service = new DeviceLocationRiskService(redisTemplate, "elvo:wallet:device-trust:", 60, 30, 100);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void shouldTreatFirstSeenContextAsTrusted() {
        UUID userId = UUID.randomUUID();
        when(hashOperations.entries("elvo:wallet:device-trust:" + userId)).thenReturn(Map.of());

        boolean risky = service.requiresAdditionalVerification(userId, "device-a", "kampala");

        assertFalse(risky);
        verify(hashOperations).put(eq("elvo:wallet:device-trust:" + userId), eq("trustScore"), eq("0"));
    }

    @Test
    void shouldFlagRiskWhenDeviceChanges() {
        UUID userId = UUID.randomUUID();
        Map<Object, Object> existing = new HashMap<>();
        existing.put("deviceId", "device-a");
        existing.put("locationHint", "kampala");
        existing.put("seenAt", String.valueOf(java.time.Instant.now().minusSeconds(120).getEpochSecond()));
        existing.put("trustScore", "0");

        when(hashOperations.entries("elvo:wallet:device-trust:" + userId)).thenReturn(existing);

        boolean risky = service.requiresAdditionalVerification(userId, "device-b", "kampala");

        assertTrue(risky);
    }

    @Test
    void shouldReduceTrustScoreWhenContextStaysStable() {
        UUID userId = UUID.randomUUID();
        Map<Object, Object> existing = new HashMap<>();
        existing.put("deviceId", "device-a");
        existing.put("locationHint", "kampala");
        existing.put("seenAt", String.valueOf(java.time.Instant.now().minusSeconds(600).getEpochSecond()));
        existing.put("trustScore", "40");

        when(hashOperations.entries("elvo:wallet:device-trust:" + userId)).thenReturn(existing);

        boolean risky = service.requiresAdditionalVerification(userId, "device-a", "kampala");

        assertFalse(risky);
        verify(hashOperations).put(eq("elvo:wallet:device-trust:" + userId), eq("trustScore"), eq("20"));
    }
}
