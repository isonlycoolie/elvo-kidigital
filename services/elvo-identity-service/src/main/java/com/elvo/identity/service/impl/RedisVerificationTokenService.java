package com.elvo.identity.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.elvo.identity.service.VerificationTokenService;

@Service
public class RedisVerificationTokenService implements VerificationTokenService {

    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final String tokenKeyPrefix;
    private final String userKeyPrefix;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisVerificationTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${elvo.security.verification-token.ttl-minutes:10}") long ttlMinutes,
            @Value("${elvo.security.verification-token.token-key-prefix:elvo:verification:token:}") String tokenKeyPrefix,
            @Value("${elvo.security.verification-token.user-key-prefix:elvo:verification:user:}") String userKeyPrefix) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(Math.max(1, ttlMinutes));
        this.tokenKeyPrefix = tokenKeyPrefix;
        this.userKeyPrefix = userKeyPrefix;
    }

    @Override
    public VerificationToken issueToken(UUID userId) {
        String userKey = userKey(userId);
        String previousToken = redisTemplate.opsForValue().get(userKey);
        if (previousToken != null && !previousToken.isBlank()) {
            redisTemplate.delete(tokenKey(previousToken));
        }

        String token = generateToken();
        redisTemplate.opsForValue().set(tokenKey(token), userId.toString(), ttl);
        redisTemplate.opsForValue().set(userKey, token, ttl);
        return new VerificationToken(token, Instant.now().plus(ttl));
    }

    @Override
    public boolean isValidForUser(String token, UUID userId) {
        if (token == null || token.isBlank() || userId == null) {
            return false;
        }
        String mappedUserId = redisTemplate.opsForValue().get(tokenKey(token.trim()));
        return userId.toString().equals(mappedUserId);
    }

    @Override
    public void invalidateForUser(UUID userId) {
        if (userId == null) {
            return;
        }
        String userKey = userKey(userId);
        String token = redisTemplate.opsForValue().get(userKey);
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(tokenKey(token));
        }
        redisTemplate.delete(userKey);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenKey(String token) {
        return tokenKeyPrefix + token;
    }

    private String userKey(UUID userId) {
        return userKeyPrefix + userId;
    }
}
