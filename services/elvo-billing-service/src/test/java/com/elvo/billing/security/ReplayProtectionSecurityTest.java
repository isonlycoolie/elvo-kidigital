package com.elvo.billing.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Security tests for replay attack prevention.
 */
@DisplayName("Replay Attack Prevention Security Tests")
public class ReplayProtectionSecurityTest {

    @BeforeEach
    void resetReplayState() {
        InternalServiceMessageAuthenticator.resetReplayCachesForTests();
    }

    @Test
    @DisplayName("Should reject message with timestamp older than expiration window")
    void testRejectOldTimestamp() {
        Instant now = Instant.now();
        Map<String, Object> event = replayEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                now.minusSeconds(600),
                now.minusSeconds(60));

        assertFalse(InternalServiceMessageAuthenticator.isReplaySafe(event));
    }

    @Test
    @DisplayName("Should reject duplicate message by messageId")
    void testRejectDuplicateMessageId() {
        String messageId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Map<String, Object> event1 = replayEvent(messageId, UUID.randomUUID().toString(), now, now.plusSeconds(120));
        Map<String, Object> event2 = replayEvent(messageId, UUID.randomUUID().toString(), now, now.plusSeconds(120));

        assertTrue(InternalServiceMessageAuthenticator.isReplaySafe(event1));
        assertFalse(InternalServiceMessageAuthenticator.isReplaySafe(event2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should reject message with missing messageId")
    void testRejectInvalidMessageId(String invalidId) {
        Instant now = Instant.now();
        Map<String, Object> event = replayEvent(invalidId, UUID.randomUUID().toString(), now, now.plusSeconds(120));

        assertFalse(InternalServiceMessageAuthenticator.isReplaySafe(event));
    }

    @Test
    @DisplayName("Should accept valid message within expiration window")
    void testAcceptValidMessage() {
        Instant now = Instant.now();
        Map<String, Object> event = replayEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                now.minusSeconds(30),
                now.plusSeconds(120));

        assertTrue(InternalServiceMessageAuthenticator.isReplaySafe(event));
    }

    private static Map<String, Object> replayEvent(
            String messageId,
            String nonce,
            Instant occurredAt,
            Instant expiresAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD, messageId);
        event.put(InternalServiceMessageAuthenticator.NONCE_FIELD, nonce);
        event.put("occurredAt", occurredAt.toString());
        event.put(InternalServiceMessageAuthenticator.EXPIRES_AT_FIELD, expiresAt.toString());
        return event;
    }
}
