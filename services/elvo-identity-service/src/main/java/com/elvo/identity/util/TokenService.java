package com.elvo.identity.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
    private static final String ROLES_CLAIM = "roles";
    private static final String SCOPES_CLAIM = "scopes";
    private static final List<String> DEFAULT_ROLES = List.of("USER");
    private static final List<String> DEFAULT_SCOPES = List.of("wallet:read", "wallet:write");

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;

    public TokenService(@Value("${elvo.security.jwt.secret}") String jwtSecret,
                        @Value("${elvo.security.jwt.issuer:elvo-identity-service}") String issuer,
                        @Value("${elvo.security.jwt.audience:elvo-platform}") String audience,
                        @Value("${elvo.security.jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes,
                        @Value("${elvo.security.jwt.refresh-token-ttl-days:7}") long refreshTokenTtlDays) {
        validateJwtSecret(jwtSecret);
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    private void validateJwtSecret(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("elvo.security.jwt.secret must be configured");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("elvo.security.jwt.secret must be at least 32 bytes");
        }
    }

    public TokenPayload generateAccessToken(UUID userId, String ean) {
        return generateAccessToken(userId, ean, DEFAULT_ROLES, DEFAULT_SCOPES);
    }

    public TokenPayload generateAccessToken(UUID userId, String ean, List<String> roles, List<String> scopes) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlMinutes * 60);
        String token = Jwts.builder()
            .issuer(issuer)
            .audience().add(audience).and()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(EAN_CLAIM, ean)
                .claim(ROLES_CLAIM, sanitizeRequiredStringListClaim(roles, ROLES_CLAIM))
                .claim(SCOPES_CLAIM, sanitizeRequiredStringListClaim(scopes, SCOPES_CLAIM))
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
            .issuer(issuer)
            .audience().add(audience).and()
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
        List<String> roles = parseRequiredStringListClaim(claims.get(ROLES_CLAIM), ROLES_CLAIM);
        List<String> scopes = parseRequiredStringListClaim(claims.get(SCOPES_CLAIM), SCOPES_CLAIM);

        return new AccessTokenClaims(userId, ean, roles, scopes, claims.getExpiration().toInstant());
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
                    .requireIssuer(issuer)
                    .requireAudience(audience)
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

    private List<String> sanitizeRequiredStringListClaim(List<String> values, String claimName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Token " + claimName + " claim is invalid");
        }
        List<String> sanitized = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Token " + claimName + " claim is invalid");
        }
        return sanitized;
    }

    private List<String> parseRequiredStringListClaim(Object claimValue, String claimName) {
        if (!(claimValue instanceof List<?> rawValues) || rawValues.isEmpty()) {
            throw new IllegalArgumentException("Token " + claimName + " claim is invalid");
        }
        List<String> parsed = rawValues.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        if (parsed.isEmpty() || parsed.size() != rawValues.size()) {
            throw new IllegalArgumentException("Token " + claimName + " claim is invalid");
        }
        return parsed;
    }

    public record TokenPayload(String token, Instant expiresAt) {
    }

    public record AccessTokenClaims(UUID userId, String ean, List<String> roles, List<String> scopes, Instant expiresAt) {
    }

    public record RefreshTokenClaims(UUID userId, Instant expiresAt) {
    }
}
