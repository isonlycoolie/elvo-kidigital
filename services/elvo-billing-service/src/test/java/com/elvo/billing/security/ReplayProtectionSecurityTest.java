package com.elvo.billing.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Security tests for replay attack prevention.
 * Verifies that the system prevents replay of messages using timestamps, nonces, and message IDs.
 */
@DisplayName("Replay Attack Prevention Security Tests")
public class ReplayProtectionSecurityTest {

    @Test
    @DisplayName("Should reject message with timestamp older than expiration window")
    void testRejectOldTimestamp() {
        Map<String, Object> event = Map.of(
                "timestamp", System.currentTimeMillis() - 600_000, // 10 minutes old
                InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD, UUID.randomUUID().toString()
        );
        
        boolean isSafe = InternalServiceMessageAuthenticator.isReplaySafe(event);
        assertFalse(isSafe, "Should reject message with expired timestamp");
    }

    @Test
    @DisplayName("Should reject duplicate message by messageId")
    void testRejectDuplicateMessageId() {
        String messageId = UUID.randomUUID().toString();
        Map<String, Object> event1 = Map.of(
                InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD, messageId,
                "timestamp", System.currentTimeMillis()
        );
        Map<String, Object> event2 = Map.of(
                InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD, messageId,
                "timestamp", System.currentTimeMillis()
        );
        
        assertTrue(InternalServiceMessageAuthenticator.isReplaySafe(event1), "First message should be safe");
        assertFalse(InternalServiceMessageAuthenticator.isReplaySafe(event2), "Duplicate message should be rejected");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-format", "12345"})
    @DisplayName("Should reject message with invalid or missing messageId")
    void testRejectInvalidMessageId(String invalidId) {
        Map<String, Object> event = Map.of(
                InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD, invalidId,
                "timestamp", System.currentTimeMillis()
        );
        
        boolean isSafe = InternalServiceMessageAuthenticator.isReplaySafe(event);
        assertFalse(isSafe, "Should reject message with invalid messageId");
    }

    @Test
    @DisplayName("Should accept valid message within expiration window")
    void testAcceptValidMessage() {
        Map<String, Object> event = Map.of(
                InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD, UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis() - 30_000 // 30 seconds old
        );
        
        boolean isSafe = InternalServiceMessageAuthenticator.isReplaySafe(event);
        assertTrue(isSafe, "Should accept message within valid window");
    }
}
