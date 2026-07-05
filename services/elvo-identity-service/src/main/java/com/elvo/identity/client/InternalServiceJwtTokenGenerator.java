package com.elvo.identity.client;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class InternalServiceJwtTokenGenerator {

    private final InternalServiceJwtProperties properties;

    public InternalServiceJwtTokenGenerator(InternalServiceJwtProperties properties) {
        this.properties = properties;
    }

    public String generateToken(String sourceServiceName) {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("elvo.security.internal-jwt.secret must be configured");
        }

        var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .subject(sourceServiceName)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(properties.getTokenTtlSeconds())))
                .claim(properties.getSourceServiceClaim(), sourceServiceName)
                .claim(properties.getServiceIdentityClaim(), sourceServiceName)
                .claim("roles", List.of(properties.getRequiredRole()))
                .signWith(key)
                .compact();
    }
}
