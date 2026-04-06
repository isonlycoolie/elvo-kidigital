package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class ApiAbuseProtectionServiceTest {

    @Test
    void shouldBlockAfterConfiguredViolationThreshold() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        ApiAbuseProtectionService service = new ApiAbuseProtectionService(
                provider,
                2,
                600,
                900,
                "abuse:test:");

        UUID userId = UUID.randomUUID();
        service.recordViolation(userId, "10.0.0.1", "device-1");
        service.recordViolation(userId, "10.0.0.1", "device-1");

        ApiAbuseProtectionService.AbuseDecision decision = service.evaluate(userId, "10.0.0.1", "device-1");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("API abuse block");
    }

    @Test
    void shouldResetBlocksOnSuccess() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        ApiAbuseProtectionService service = new ApiAbuseProtectionService(
            provider,
                1,
                600,
                900,
                "abuse:test:");

        UUID userId = UUID.randomUUID();
        service.recordViolation(userId, "10.0.0.2", "device-2");
        assertThat(service.evaluate(userId, "10.0.0.2", "device-2").allowed()).isFalse();

        service.recordSuccess(userId, "10.0.0.2", "device-2");
        assertThat(service.evaluate(userId, "10.0.0.2", "device-2").allowed()).isTrue();
    }
}
