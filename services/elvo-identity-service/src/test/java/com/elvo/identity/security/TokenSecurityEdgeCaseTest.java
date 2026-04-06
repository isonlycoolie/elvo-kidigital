package com.elvo.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.elvo.identity.util.TokenService;

class TokenSecurityEdgeCaseTest {

    private static final String ISSUER = "identity-test-issuer";
    private static final String AUDIENCE = "identity-test-audience";
    private static final String KEY_ID = "identity-edge-key";
    private static final long SESSION_ABSOLUTE_TTL_DAYS = 30;

    private TokenService tokenService(long accessMinutes, long refreshDays) {
        KeyPair keyPair = generateRsaKeyPair();
        return tokenService(keyPair, ISSUER, AUDIENCE, accessMinutes, refreshDays);
    }

    private TokenService tokenService(KeyPair keyPair, String issuer, String audience, long accessMinutes, long refreshDays) {
        return new TokenService(
                "",
                toPrivatePem(keyPair),
                toPublicPem(keyPair),
                KEY_ID,
                issuer,
                audience,
                accessMinutes,
                refreshDays,
                SESSION_ABSOLUTE_TTL_DAYS);
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

    private String toPrivatePem(KeyPair keyPair) {
        String encoded = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
    }

    private String toPublicPem(KeyPair keyPair) {
        String encoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }

    @Test
    void invalidJwtShouldBeRejected() {
        TokenService tokenService = tokenService(15, 7);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken("abc.def.ghi"));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void tamperedJwtShouldBeRejected() {
        TokenService tokenService = tokenService(15, 7);
        TokenService.TokenPayload tokenPayload = tokenService.generateAccessToken(UUID.randomUUID(), "ELVO-EDGE-0001");

        String[] parts = tokenPayload.token().split("\\.");
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1)
            + (parts[1].endsWith("A") ? "B" : "A");
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken(tampered));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void expiredRefreshTokenShouldBeRejected() {
        TokenService tokenService = tokenService(-1, -1);
        TokenService.TokenPayload refresh = tokenService.generateRefreshToken(UUID.randomUUID());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateRefreshToken(refresh.token()));
        assertNotNull(ex);
    }

    @Test
    void wrongIssuerShouldBeRejected() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService validatingService = tokenService(keyPair, ISSUER, AUDIENCE, 15, 7);
        TokenService foreignIssuerService = tokenService(keyPair, "other-issuer", AUDIENCE, 15, 7);

        TokenService.TokenPayload tokenPayload = foreignIssuerService.generateAccessToken(UUID.randomUUID(), "ELVO-EDGE-0001");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validatingService.validateAccessToken(tokenPayload.token()));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void wrongAudienceShouldBeRejected() {
        KeyPair keyPair = generateRsaKeyPair();
        TokenService validatingService = tokenService(keyPair, ISSUER, AUDIENCE, 15, 7);
        TokenService foreignAudienceService = tokenService(keyPair, ISSUER, "other-audience", 15, 7);

        TokenService.TokenPayload tokenPayload = foreignAudienceService.generateAccessToken(UUID.randomUUID(), "ELVO-EDGE-0001");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validatingService.validateAccessToken(tokenPayload.token()));
        assertEquals("Token is invalid", ex.getMessage());
    }
}
