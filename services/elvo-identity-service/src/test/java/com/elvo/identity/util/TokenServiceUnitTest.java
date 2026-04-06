package com.elvo.identity.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;

class TokenServiceUnitTest {

    private static final String SECRET = "identity-unit-test-secret-at-least-thirty-two-bytes";
    private static final String ISSUER = "identity-test-issuer";
    private static final String AUDIENCE = "identity-test-audience";
    private static final String KEY_ID = "identity-key-01";

    private TokenService tokenService(KeyPair keyPair, String keyId, long accessMinutes, long refreshDays) {
        return new TokenService(keyPair.getPrivate(), keyPair.getPublic(), ISSUER, AUDIENCE, keyId, accessMinutes, refreshDays);
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate test key pair", ex);
        }
    }

    @Test
    void generateAndValidateAccessTokenShouldReturnExpectedClaims() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, 15, 7);
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
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, 15, 7);
        TokenService.TokenPayload accessToken = tokenService.generateAccessToken(UUID.randomUUID(), "ELVO-UNIT-000001");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateRefreshToken(accessToken.token()));
        assertEquals("Token type is invalid", ex.getMessage());
    }

    @Test
    void validateAccessTokenShouldRejectMalformedToken() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, 15, 7);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken("not.a.valid.jwt"));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void validateRefreshTokenShouldRejectExpiredToken() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, -1, -1);
        TokenService.TokenPayload refreshToken = tokenService.generateRefreshToken(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> tokenService.validateRefreshToken(refreshToken.token()));
    }

    @Test
    void generatedTokenShouldContainFutureExpiry() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, 5, 1);

        TokenService.TokenPayload tokenPayload = tokenService.generateRefreshToken(UUID.randomUUID());
        assertDoesNotThrow(() -> tokenService.validateRefreshToken(tokenPayload.token()));
        assertNotNull(tokenPayload.expiresAt());
        assertEquals(true, tokenPayload.expiresAt().isAfter(Instant.now().minusSeconds(1)));
    }

    @Test
    void generateAccessTokenShouldAllowCustomRolesAndScopes() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, 15, 7);
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
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, 15, 7);
        UUID userId = UUID.randomUUID();

        String token = Jwts.builder()
                .issuer(ISSUER)
            .header().keyId(KEY_ID).and()
                .audience().add(AUDIENCE).and()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("ean", "ELVO-UNIT-ROLES")
                .claim("tokenType", "ACCESS")
                .claim("scopes", List.of("wallet:read"))
            .signWith(keyPair.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken(token));
        assertEquals("Token roles claim is invalid", ex.getMessage());
    }

    @Test
    void validateAccessTokenShouldRejectMalformedScopesClaim() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, 15, 7);
        UUID userId = UUID.randomUUID();

        String token = Jwts.builder()
                .issuer(ISSUER)
            .header().keyId(KEY_ID).and()
                .audience().add(AUDIENCE).and()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("ean", "ELVO-UNIT-SCOPES")
                .claim("tokenType", "ACCESS")
                .claim("roles", List.of("USER"))
                .claim("scopes", "wallet:read")
            .signWith(keyPair.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken(token));
        assertEquals("Token scopes claim is invalid", ex.getMessage());
    }

    @Test
    void validateAccessTokenShouldRejectUnexpectedKeyId() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService issuerService = tokenService(keyPair, "key-a", 15, 7);
        TokenService verifierService = tokenService(keyPair, "key-b", 15, 7);

        TokenService.TokenPayload tokenPayload = issuerService.generateAccessToken(UUID.randomUUID(), "ELVO-UNIT-KID");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> verifierService.validateAccessToken(tokenPayload.token()));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void jwksShouldExposeRsaSigningMetadata() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService tokenService = tokenService(keyPair, KEY_ID, 15, 7);

        TokenService.JwksDocument jwks = tokenService.getJwksDocument();
        assertEquals(1, jwks.keys().size());
        TokenService.JwkKey key = jwks.keys().get(0);
        assertEquals(KEY_ID, key.kid());
        assertEquals("RSA", key.kty());
        assertEquals("RS256", key.alg());
        assertEquals("sig", key.use());
        assertNotNull(key.n());
        assertNotNull(key.e());
    }

    @Test
    void jwksShouldRejectSymmetricMode() {
        TokenService tokenService = new TokenService(SECRET, "", "", "", ISSUER, AUDIENCE, 15, 7);

        IllegalStateException ex = assertThrows(IllegalStateException.class, tokenService::getJwksDocument);
        assertEquals("JWKS requires asymmetric signing configuration", ex.getMessage());
    }
}
