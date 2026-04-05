package com.elvo.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.elvo.identity.util.TokenService;

class TokenSecurityEdgeCaseTest {

    private static final String SECRET = "identity-security-edge-secret-at-least-thirty-two-bytes";

    @Test
    void invalidJwtShouldBeRejected() {
        TokenService tokenService = new TokenService(SECRET, 15, 7);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken("abc.def.ghi"));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void tamperedJwtShouldBeRejected() {
        TokenService tokenService = new TokenService(SECRET, 15, 7);
        TokenService.TokenPayload tokenPayload = tokenService.generateAccessToken(UUID.randomUUID(), "ELVO-EDGE-0001");

        String tampered = tokenPayload.token().substring(0, tokenPayload.token().length() - 2) + "zz";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken(tampered));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void expiredRefreshTokenShouldBeRejected() {
        TokenService tokenService = new TokenService(SECRET, -1, -1);
        TokenService.TokenPayload refresh = tokenService.generateRefreshToken(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> tokenService.validateRefreshToken(refresh.token()));
    }
}
