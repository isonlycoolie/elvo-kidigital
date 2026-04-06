package com.elvo.wallet.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisUserTokenRevocationChecker implements UserTokenRevocationChecker {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final String namespace;

    public RedisUserTokenRevocationChecker(StringRedisTemplate redisTemplate,
                                           @Value("${elvo.security.jwt.revocation.key-prefix:elvo:jwt:revoked:}") String keyPrefix,
                                           @Value("${elvo.security.jwt.revocation.namespace:elvo:shared}") String namespace) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.namespace = namespace;
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
