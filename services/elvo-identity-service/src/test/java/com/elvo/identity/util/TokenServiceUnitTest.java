package com.elvo.identity.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class TokenServiceUnitTest {

    private static final String SECRET = "identity-unit-test-secret-at-least-thirty-two-bytes";
    private static final String ISSUER = "identity-test-issuer";
    private static final String AUDIENCE = "identity-test-audience";

    private TokenService tokenService(long accessMinutes, long refreshDays) {
        return new TokenService(SECRET, ISSUER, AUDIENCE, accessMinutes, refreshDays);
    }

    @Test
    void generateAndValidateAccessTokenShouldReturnExpectedClaims() {
        TokenService tokenService = tokenService(15, 7);
        UUID userId = UUID.randomUUID();

        TokenService.TokenPayload payload = tokenService.generateAccessToken(userId, "ELVO-UNIT-123456");
        TokenService.AccessTokenClaims claims = tokenService.validateAccessToken(payload.token());

        assertEquals(userId, claims.userId());
        assertEquals("ELVO-UNIT-123456", claims.ean());
        assertEquals(List.of("USER"), claims.roles());
        assertEquals(List.of("wallet:read", "wallet:write"), claims.scopes());
        assertNotNull(claims.expiresAt());
    }

    @Test
    void validateRefreshTokenShouldRejectAccessToken() {
        TokenService tokenService = tokenService(15, 7);
        TokenService.TokenPayload accessToken = tokenService.generateAccessToken(UUID.randomUUID(), "ELVO-UNIT-000001");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateRefreshToken(accessToken.token()));
        assertEquals("Token type is invalid", ex.getMessage());
    }

    @Test
    void validateAccessTokenShouldRejectMalformedToken() {
        TokenService tokenService = tokenService(15, 7);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken("not.a.valid.jwt"));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void validateRefreshTokenShouldRejectExpiredToken() {
        TokenService tokenService = tokenService(-1, -1);
        TokenService.TokenPayload refreshToken = tokenService.generateRefreshToken(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> tokenService.validateRefreshToken(refreshToken.token()));
    }

    @Test
    void generatedTokenShouldContainFutureExpiry() {
        TokenService tokenService = tokenService(5, 1);

        TokenService.TokenPayload tokenPayload = tokenService.generateRefreshToken(UUID.randomUUID());
        assertDoesNotThrow(() -> tokenService.validateRefreshToken(tokenPayload.token()));
        assertNotNull(tokenPayload.expiresAt());
        assertEquals(true, tokenPayload.expiresAt().isAfter(Instant.now().minusSeconds(1)));
    }

    @Test
    void generateAccessTokenShouldAllowCustomRolesAndScopes() {
        TokenService tokenService = tokenService(15, 7);
        UUID userId = UUID.randomUUID();

        TokenService.TokenPayload payload = tokenService.generateAccessToken(
                userId,
                "ELVO-UNIT-777777",
                List.of("ADMIN", "AUDIT_ADMIN"),
                List.of("wallet:read", "wallet:admin"));

        TokenService.AccessTokenClaims claims = tokenService.validateAccessToken(payload.token());
        assertEquals(List.of("ADMIN", "AUDIT_ADMIN"), claims.roles());
        assertEquals(List.of("wallet:read", "wallet:admin"), claims.scopes());
    }

    @Test
    void validateAccessTokenShouldRejectMissingRolesClaim() {
        TokenService tokenService = tokenService(15, 7);
        UUID userId = UUID.randomUUID();
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes());

        String token = Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("ean", "ELVO-UNIT-ROLES")
                .claim("tokenType", "ACCESS")
                .claim("scopes", List.of("wallet:read"))
                .signWith(secretKey)
                .compact();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken(token));
        assertEquals("Token roles claim is invalid", ex.getMessage());
    }

    @Test
    void validateAccessTokenShouldRejectMalformedScopesClaim() {
        TokenService tokenService = tokenService(15, 7);
        UUID userId = UUID.randomUUID();
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes());

        String token = Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("ean", "ELVO-UNIT-SCOPES")
                .claim("tokenType", "ACCESS")
                .claim("roles", List.of("USER"))
                .claim("scopes", "wallet:read")
                .signWith(secretKey)
                .compact();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken(token));
        assertEquals("Token scopes claim is invalid", ex.getMessage());
    }
}
