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

    public TokenRevocationService(StringRedisTemplate redisTemplate,
                                  @Value("${elvo.security.jwt.revocation.key-prefix:elvo:jwt:revoked:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    public void revokeJti(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }

        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        redisTemplate.opsForValue().set(keyPrefix + jti, "1", ttl);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix + jti));
    }
}
