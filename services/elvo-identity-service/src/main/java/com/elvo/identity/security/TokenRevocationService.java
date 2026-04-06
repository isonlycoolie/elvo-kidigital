package com.elvo.identity.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenRevocationService implements TokenRevocationChecker {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final String namespace;

    public TokenRevocationService(StringRedisTemplate redisTemplate,
                                  @Value("${elvo.security.jwt.revocation.key-prefix:elvo:jwt:revoked:}") String keyPrefix,
                                  @Value("${elvo.security.jwt.revocation.namespace:elvo:shared}") String namespace) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.namespace = namespace;
    }

    public void revokeJti(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }

        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        redisTemplate.opsForValue().set(buildKey(jti), "1", ttl);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(jti)));
    }

    private String buildKey(String jti) {
        if (namespace == null || namespace.isBlank()) {
            return keyPrefix + jti;
        }
        return keyPrefix + namespace + ":" + jti;
    }
}
