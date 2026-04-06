package com.elvo.identity.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.elvo.identity.service.VerificationTokenService;

@Service
public class RedisVerificationTokenService implements VerificationTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String ISSUE_SCRIPT = """
            local previousToken = redis.call('GET', KEYS[1])
            if previousToken then
                redis.call('DEL', KEYS[3] .. previousToken)
            end
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
            redis.call('SET', KEYS[1], ARGV[3], 'EX', ARGV[2])
            return 1
            """;
    private static final String INVALIDATE_SCRIPT = """
            local token = redis.call('GET', KEYS[1])
            if token then
                redis.call('DEL', KEYS[2] .. token)
            end
            redis.call('DEL', KEYS[1])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final String tokenKeyPrefix;
    private final String userKeyPrefix;
    private final SecureRandom secureRandom = new SecureRandom();
    private final RedisScript<Long> issueScript;
    private final RedisScript<Long> invalidateScript;

    public RedisVerificationTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${elvo.security.verification-token.ttl-minutes:10}") long ttlMinutes,
            @Value("${elvo.security.verification-token.token-key-prefix:elvo:verification:token:}") String tokenKeyPrefix,
            @Value("${elvo.security.verification-token.user-key-prefix:elvo:verification:user:}") String userKeyPrefix) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(Math.max(1, ttlMinutes));
        this.tokenKeyPrefix = tokenKeyPrefix;
        this.userKeyPrefix = userKeyPrefix;
        this.issueScript = RedisScript.of(ISSUE_SCRIPT, Long.class);
        this.invalidateScript = RedisScript.of(INVALIDATE_SCRIPT, Long.class);
    }

    @Override
    public VerificationToken issueToken(UUID userId) {
        String userKey = userKey(userId);
        String token = generateToken();
        long ttlSeconds = Math.max(1L, ttl.getSeconds());
        redisTemplate.execute(
                issueScript,
                List.of(userKey, tokenKey(token), tokenKeyPrefix),
                userId.toString(),
                String.valueOf(ttlSeconds),
                token);
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
        redisTemplate.execute(invalidateScript, List.of(userKey(userId), tokenKeyPrefix));
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
