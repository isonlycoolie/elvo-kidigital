package com.elvo.billing.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CallbackVerificationServiceTest {

    private static final String PROVIDER_ID = "selcom";
    private static final String PROVIDER_SECRET = "test-secret-key-12345";
    private static final String PAYLOAD = "{\"referenceNumber\":\"REF123\",\"status\":\"SUCCESS\"}";

    private CallbackVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new CallbackVerificationService();
        verificationService.setProviderSecret(PROVIDER_ID, PROVIDER_SECRET);
        verificationService.initializeAllowlist(java.util.List.of("127.0.0.1"));
        verificationService.resetNonceCacheForTests();
        InternalServiceMessageAuthenticator.resetReplayCachesForTests();
    }

    @Test
    void verifiesValidCallback() throws Exception {
        assertDoesNotThrow(() -> verificationService.verifyCallback(
                PAYLOAD,
                signatureFor(PAYLOAD, PROVIDER_SECRET),
                "nonce-1",
                Instant.now().toString(),
                "127.0.0.1",
                PROVIDER_ID));
    }

    @Test
    void rejectsInvalidSignature() {
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> verificationService.verifyCallback(
                PAYLOAD,
                "invalid-signature",
                "nonce-2",
                Instant.now().toString(),
                "127.0.0.1",
                PROVIDER_ID));
    }

    @Test
    void rejectsReplayNonce() throws Exception {
        String signature = signatureFor(PAYLOAD, PROVIDER_SECRET);

        assertDoesNotThrow(() -> verificationService.verifyCallback(
                PAYLOAD, signature, "nonce-replay", Instant.now().toString(), "127.0.0.1", PROVIDER_ID));

        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> verificationService.verifyCallback(
                PAYLOAD, signature, "nonce-replay", Instant.now().toString(), "127.0.0.1", PROVIDER_ID));
    }

    @Test
    void rejectsUntrustedSourceIp() {
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> verificationService.verifyCallback(
                PAYLOAD,
                signatureFor(PAYLOAD, PROVIDER_SECRET),
                "nonce-3",
                Instant.now().toString(),
                "10.0.0.5",
                PROVIDER_ID));
    }

    private static String signatureFor(String payload, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
