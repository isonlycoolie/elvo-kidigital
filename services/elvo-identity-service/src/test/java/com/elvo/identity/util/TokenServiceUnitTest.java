package com.elvo.identity.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TokenServiceUnitTest {

    private static final String SECRET = "identity-unit-test-secret-at-least-thirty-two-bytes";

    @Test
    void generateAndValidateAccessTokenShouldReturnExpectedClaims() {
        TokenService tokenService = new TokenService(SECRET, 15, 7);
        UUID userId = UUID.randomUUID();

        TokenService.TokenPayload payload = tokenService.generateAccessToken(userId, "ELVO-UNIT-123456");
        TokenService.AccessTokenClaims claims = tokenService.validateAccessToken(payload.token());

        assertEquals(userId, claims.userId());
        assertEquals("ELVO-UNIT-123456", claims.ean());
        assertNotNull(claims.expiresAt());
    }

    @Test
    void validateRefreshTokenShouldRejectAccessToken() {
        TokenService tokenService = new TokenService(SECRET, 15, 7);
        TokenService.TokenPayload accessToken = tokenService.generateAccessToken(UUID.randomUUID(), "ELVO-UNIT-000001");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateRefreshToken(accessToken.token()));
        assertEquals("Token type is invalid", ex.getMessage());
    }

    @Test
    void validateAccessTokenShouldRejectMalformedToken() {
        TokenService tokenService = new TokenService(SECRET, 15, 7);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken("not.a.valid.jwt"));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void validateRefreshTokenShouldRejectExpiredToken() {
        TokenService tokenService = new TokenService(SECRET, -1, -1);
        TokenService.TokenPayload refreshToken = tokenService.generateRefreshToken(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> tokenService.validateRefreshToken(refreshToken.token()));
    }

    @Test
    void generatedTokenShouldContainFutureExpiry() {
        TokenService tokenService = new TokenService(SECRET, 5, 1);

        TokenService.TokenPayload tokenPayload = tokenService.generateRefreshToken(UUID.randomUUID());
        assertDoesNotThrow(() -> tokenService.validateRefreshToken(tokenPayload.token()));
        assertNotNull(tokenPayload.expiresAt());
        assertEquals(true, tokenPayload.expiresAt().isAfter(Instant.now().minusSeconds(1)));
    }
}
