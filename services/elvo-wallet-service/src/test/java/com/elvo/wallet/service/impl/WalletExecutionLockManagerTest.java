package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class WalletExecutionLockManagerTest {

    @Test
    void shouldAcquireAndReleaseDistributedLock() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(10_000L), eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        WalletExecutionLockManager manager = new WalletExecutionLockManager(
                redisTemplate,
                true,
                "wallet:lock:",
                10,
                3,
                1);

        ReentrantLock lock = manager.lock("wallet-1");
        manager.unlock("wallet-1", lock);

        verify(valueOperations).setIfAbsent(anyString(), anyString(), eq(10_000L), eq(TimeUnit.MILLISECONDS));
        verify(redisTemplate).execute(any(), eq(java.util.List.of("wallet:lock:wallet-1")), anyString());
    }

    @Test
    void shouldFailWhenDistributedLockCannotBeAcquired() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Long.class), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        WalletExecutionLockManager manager = new WalletExecutionLockManager(
                redisTemplate,
                true,
                "wallet:lock:",
                10,
                2,
                1);

        assertThatThrownBy(() -> manager.lock("wallet-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("distributed wallet lock");
    }
}
