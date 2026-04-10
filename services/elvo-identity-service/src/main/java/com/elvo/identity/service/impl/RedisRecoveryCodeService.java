package com.elvo.identity.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.RecoveryCodeService;
import com.elvo.identity.service.TotpManagementService;

@Service
public class RedisRecoveryCodeService implements RecoveryCodeService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final StringRedisTemplate redisTemplate;
    private final SecurityHashingService hashingService;
    private final TotpManagementService totpManagementService;
    private final int codeCount;
    private final int codeLength;
    private final Duration ttl;
    private final String keyPrefix;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisRecoveryCodeService(
            StringRedisTemplate redisTemplate,
            SecurityHashingService hashingService,
            TotpManagementService totpManagementService,
            @Value("${elvo.security.totp.recovery.code-count:8}") int codeCount,
            @Value("${elvo.security.totp.recovery.code-length:10}") int codeLength,
            @Value("${elvo.security.totp.recovery.ttl-days:365}") long ttlDays,
            @Value("${elvo.security.totp.recovery.key-prefix:elvo:totp:recovery:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.hashingService = hashingService;
        this.totpManagementService = totpManagementService;
        this.codeCount = Math.max(1, codeCount);
        this.codeLength = Math.max(6, codeLength);
        this.ttl = Duration.ofDays(Math.max(1, ttlDays));
        this.keyPrefix = keyPrefix;
    }

    @Override
    public RecoveryCodeBatch issueCodes(UUID userId, String totpCode) {
        boolean verified = totpManagementService.verifyActiveCode(userId, totpCode);
        if (!verified) {
            throw new IllegalArgumentException("TOTP verification is required to issue recovery codes");
        }

        String key = key(userId);
        redisTemplate.delete(key);

        List<String> rawCodes = new ArrayList<>(codeCount);
        List<String> hashedCodes = new ArrayList<>(codeCount);
        for (int i = 0; i < codeCount; i++) {
            String code = generateCode();
            rawCodes.add(code);
            hashedCodes.add(hashingService.hashOneTimeCode(code));
        }
        if (!hashedCodes.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, hashedCodes);
            redisTemplate.expire(key, ttl);
        }
        return new RecoveryCodeBatch(rawCodes, hashedCodes.size());
    }

    @Override
    public boolean consumeCode(UUID userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String key = key(userId);
        List<String> hashedCodes = redisTemplate.opsForList().range(key, 0, -1);
        if (hashedCodes == null || hashedCodes.isEmpty()) {
            return false;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (String hashed : hashedCodes) {
            if (hashingService.verifyOneTimeCode(normalized, hashed)) {
                redisTemplate.opsForList().remove(key, 1, hashed);
                return true;
            }
        }
        return false;
    }

    @Override
    public int remainingCodes(UUID userId) {
        Long size = redisTemplate.opsForList().size(key(userId));
        return size == null ? 0 : size.intValue();
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            int idx = secureRandom.nextInt(ALPHABET.length());
            code.append(ALPHABET.charAt(idx));
        }
        return code.toString();
    }

    private String key(UUID userId) {
        return keyPrefix + userId;
    }
}