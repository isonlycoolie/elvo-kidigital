package com.elvo.billing.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for CallbackVerificationService.
 * Tests signature validation, timestamp, source IP, and replay protection.
 */
public class CallbackVerificationServiceTest {

    private CallbackVerificationService verificationService;
    private static final String TEST_PROVIDER_SECRET = "test-secret-key-12345";
    private static final String TEST_PROVIDER_ID = "selcom";
    private static final String TEST_CALLBACK_PAYLOAD = "{\"referenceNumber\":\"REF123\",\"status\":\"SUCCESS\"}";

    @BeforeEach
    void setup() {
        verificationService = new CallbackVerificationService();
        // Mock provider secret resolution via reflection or direct config
        verificationService.initializeAllowlist(java.util.List.of("127.0.0.1", "192.168.1.1"));
    }
        verificationService.setProviderSecret(TEST_PROVIDER_ID, TEST_PROVIDER_SECRET);
        verificationService.initializeAllowlist(java.util.List.of("127.0.0.1", "192.168.1.1"));
    }

    @Test
    void testValidCallbackVerification() throws Exception {
        // Arrange
        String signature = computeHmacSha256(TEST_CALLBACK_PAYLOAD, TEST_PROVIDER_SECRET);
        String nonce = "unique-nonce-123";
        String timestamp = Instant.now().toString();
        String sourceIp = "127.0.0.1";

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, signature, nonce, timestamp, sourceIp, TEST_PROVIDER_ID)
        );
    }

    @Test
    void testInvalidSignatureRejected() throws Exception {
        // Arrange
        String invalidSignature = "invalid-signature-xyz";
        String nonce = "unique-nonce-456";
        String timestamp = Instant.now().toString();
        String sourceIp = "127.0.0.1";

        // Act & Assert
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, invalidSignature, nonce, timestamp, sourceIp, TEST_PROVIDER_ID)
        );
    }

    @Test
    void testMissingSignatureHeader() {
        // Arrange & Act & Assert
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, null, "nonce", Instant.now().toString(), "127.0.0.1", TEST_PROVIDER_ID)
        );
    }

    @Test
    void testMissingNonceHeader() {
        // Arrange
        String signature = computeHmacSha256(TEST_CALLBACK_PAYLOAD, TEST_PROVIDER_SECRET);

        // Act & Assert
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, signature, null, Instant.now().toString(), "127.0.0.1", TEST_PROVIDER_ID)
        );
    }

    @Test
    void testMissingTimestampHeader() throws Exception {
        // Arrange
        String signature = computeHmacSha256(TEST_CALLBACK_PAYLOAD, TEST_PROVIDER_SECRET);

        // Act & Assert
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, signature, "nonce", null, "127.0.0.1", TEST_PROVIDER_ID)
        );
    }

    @Test
    void testOldTimestampRejected() throws Exception {
        // Arrange - timestamp 10 minutes old
        String signature = computeHmacSha256(TEST_CALLBACK_PAYLOAD, TEST_PROVIDER_SECRET);
        String nonce = "unique-nonce-old";
        Instant oldTime = Instant.now().minusSeconds(600);
        String timestamp = oldTime.toString();

        // Act & Assert
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, signature, nonce, timestamp, "127.0.0.1", TEST_PROVIDER_ID)
        );
    }

    @Test
    void testSourceIpValidation() throws Exception {
        // Arrange
        String signature = computeHmacSha256(TEST_CALLBACK_PAYLOAD, TEST_PROVIDER_SECRET);
        String nonce = "unique-nonce-789";
        String timestamp = Instant.now().toString();
        String untrustedIp = "10.0.0.1"; // Not in allowlist

        // Act & Assert
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, signature, nonce, timestamp, untrustedIp, TEST_PROVIDER_ID)
        );
    }

    @Test
    void testReplayProtection() throws Exception {
        // Arrange
        String signature = computeHmacSha256(TEST_CALLBACK_PAYLOAD, TEST_PROVIDER_SECRET);
        String nonce = "unique-nonce-replay";
        String timestamp = Instant.now().toString();
        String sourceIp = "127.0.0.1";

        // Act - first call succeeds
        assertDoesNotThrow(() -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, signature, nonce, timestamp, sourceIp, TEST_PROVIDER_ID)
        );

        // Act & Assert - second call with same nonce fails (replay)
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> 
            verificationService.verifyCallback(TEST_CALLBACK_PAYLOAD, signature, nonce, timestamp, sourceIp, TEST_PROVIDER_ID)
        );
    }

    @Test
    void testEmptyPayload() throws Exception {
        // Arrange
        String signature = computeHmacSha256("", TEST_PROVIDER_SECRET);

        // Act & Assert
        assertThrows(CallbackVerificationService.CallbackVerificationException.class, () -> 
            verificationService.verifyCallback("", signature, "nonce", Instant.now().toString(), "127.0.0.1", TEST_PROVIDER_ID)
        );
    }

    /**
     * Helper: compute HMAC-SHA256 signature for testing.
     */
    private String computeHmacSha256(String payload, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "HmacSHA256"
        ));
        byte[] digest = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
