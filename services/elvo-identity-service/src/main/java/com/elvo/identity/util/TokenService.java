package com.elvo.identity.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class TokenService {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";
    private static final String EAN_CLAIM = "ean";

    private final SecretKey signingKey;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;

    public TokenService(@Value("${elvo.security.jwt.secret}") String jwtSecret,
                        @Value("${elvo.security.jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes,
                        @Value("${elvo.security.jwt.refresh-token-ttl-days:7}") long refreshTokenTtlDays) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public TokenPayload generateAccessToken(UUID userId, String ean) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlMinutes * 60);
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(EAN_CLAIM, ean)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .id(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
        return new TokenPayload(token, expiresAt);
    }

    public TokenPayload generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(refreshTokenTtlDays * 24 * 60 * 60);
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .id(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
        return new TokenPayload(token, expiresAt);
    }

    public AccessTokenClaims validateAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("Token type is invalid");
        }

        UUID userId = parseRequiredUuid(claims.getSubject());
        String ean = claims.get(EAN_CLAIM, String.class);
        if (ean == null || ean.isBlank()) {
            throw new IllegalArgumentException("Token payload is invalid");
        }

        return new AccessTokenClaims(userId, ean, claims.getExpiration().toInstant());
    }

    public RefreshTokenClaims validateRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("Token type is invalid");
        }

        UUID userId = parseRequiredUuid(claims.getSubject());
        return new RefreshTokenClaims(userId, claims.getExpiration().toInstant());
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Token is invalid", ex);
        }
    }

    private UUID parseRequiredUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Token subject is invalid", ex);
        }
    }

    public record TokenPayload(String token, Instant expiresAt) {
    }

    public record AccessTokenClaims(UUID userId, String ean, Instant expiresAt) {
    }

    public record RefreshTokenClaims(UUID userId, Instant expiresAt) {
    }
}
