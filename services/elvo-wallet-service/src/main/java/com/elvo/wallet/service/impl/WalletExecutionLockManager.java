package com.elvo.wallet.service.impl;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class WalletExecutionLockManager {

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> distributedTokens = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final boolean distributedLockingEnabled;
    private final String lockKeyPrefix;
    private final Duration lockTtl;
    private final int maxAcquireAttempts;
    private final Duration acquireRetryDelay;
    private final DefaultRedisScript<Long> releaseScript;

    public WalletExecutionLockManager(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${elvo.security.locking.distributed.enabled:true}") boolean distributedLockingEnabled,
            @Value("${elvo.security.locking.distributed.key-prefix:elvo:wallet:lock:}") String lockKeyPrefix,
            @Value("${elvo.security.locking.distributed.ttl-seconds:10}") int lockTtlSeconds,
            @Value("${elvo.security.locking.distributed.max-acquire-attempts:40}") int maxAcquireAttempts,
            @Value("${elvo.security.locking.distributed.retry-delay-ms:50}") long acquireRetryDelayMs) {
        this(
                redisTemplateProvider.getIfAvailable(),
                distributedLockingEnabled,
                lockKeyPrefix,
                lockTtlSeconds,
                maxAcquireAttempts,
                acquireRetryDelayMs);
    }

    WalletExecutionLockManager(StringRedisTemplate redisTemplate,
                               boolean distributedLockingEnabled,
                               String lockKeyPrefix,
                               int lockTtlSeconds,
                               int maxAcquireAttempts,
                               long acquireRetryDelayMs) {
        this.redisTemplate = redisTemplate;
        this.distributedLockingEnabled = distributedLockingEnabled;
        this.lockKeyPrefix = lockKeyPrefix == null || lockKeyPrefix.isBlank() ? "elvo:wallet:lock:" : lockKeyPrefix;
        this.lockTtl = Duration.ofSeconds(Math.max(3, lockTtlSeconds));
        this.maxAcquireAttempts = Math.max(1, maxAcquireAttempts);
        this.acquireRetryDelay = Duration.ofMillis(Math.max(5L, acquireRetryDelayMs));
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setResultType(Long.class);
        this.releaseScript.setScriptText("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                  return redis.call('DEL', KEYS[1])
                end
                return 0
                """);
    }

    public ReentrantLock lock(String key) {
        ReentrantLock lock = lockMap.computeIfAbsent(key, unused -> new ReentrantLock());
        lock.lock();

        boolean distributedAcquired = acquireDistributedLock(key);
        if (!distributedAcquired) {
            lock.unlock();
            if (!lock.isLocked()) {
                lockMap.remove(key, lock);
            }
            throw new IllegalStateException("Unable to acquire distributed wallet lock for key=" + key);
        }
        return lock;
    }

    public void unlock(String key, ReentrantLock lock) {
        try {
            releaseDistributedLock(key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } finally {
            if (!lock.isLocked()) {
                lockMap.remove(key, lock);
            }
        }
    }

    private boolean acquireDistributedLock(String key) {
        if (!distributedLockingEnabled || redisTemplate == null || key == null || key.isBlank()) {
            return true;
        }

        String redisKey = lockKeyPrefix + key;
        String token = java.util.UUID.randomUUID() + ":" + Thread.currentThread().getId();

        for (int attempt = 1; attempt <= maxAcquireAttempts; attempt++) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        redisKey,
                        token,
                        lockTtl.toMillis(),
                        TimeUnit.MILLISECONDS);
                if (Boolean.TRUE.equals(acquired)) {
                    distributedTokens.put(redisKey, token);
                    return true;
                }
            } catch (RuntimeException ex) {
                return true;
            }
            pause(acquireRetryDelay);
        }
        return false;
    }

    private void releaseDistributedLock(String key) {
        if (!distributedLockingEnabled || redisTemplate == null || key == null || key.isBlank()) {
            return;
        }

        String redisKey = lockKeyPrefix + key;
        String token = distributedTokens.remove(redisKey);
        if (token == null) {
            return;
        }

        try {
            redisTemplate.execute(releaseScript, java.util.List.of(redisKey), token);
        } catch (RuntimeException ignored) {
            // Best effort distributed lock release.
        }
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
