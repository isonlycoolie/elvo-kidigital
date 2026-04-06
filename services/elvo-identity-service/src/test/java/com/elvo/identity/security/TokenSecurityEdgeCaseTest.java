package com.elvo.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.elvo.identity.util.TokenService;

class TokenSecurityEdgeCaseTest {

    private static final String ISSUER = "identity-test-issuer";
    private static final String AUDIENCE = "identity-test-audience";
    private static final String KEY_ID = "identity-edge-key";

    private TokenService tokenService(long accessMinutes, long refreshDays) {
        KeyPair keyPair = generateRsaKeyPair();
        return new TokenService(
                "",
                toPrivatePem(keyPair),
                toPublicPem(keyPair),
                KEY_ID,
                ISSUER,
                AUDIENCE,
                accessMinutes,
                refreshDays);
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

        String tampered = tokenPayload.token().substring(0, tokenPayload.token().length() - 2) + "zz";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tokenService.validateAccessToken(tampered));
        assertEquals("Token is invalid", ex.getMessage());
    }

    @Test
    void expiredRefreshTokenShouldBeRejected() {
        TokenService tokenService = tokenService(-1, -1);
        TokenService.TokenPayload refresh = tokenService.generateRefreshToken(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> tokenService.validateRefreshToken(refresh.token()));
    }
}
