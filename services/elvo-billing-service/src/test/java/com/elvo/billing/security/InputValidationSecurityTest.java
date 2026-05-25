package com.elvo.billing.security;

import static org.junit.jupiter.api.Assertions.*;

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
 * Security tests for input validation and negative-path scenarios.
 * Verifies that invalid, malformed, and hostile input is properly rejected.
 */
@DisplayName("Input Validation and Negative-Path Security Tests")
public class InputValidationSecurityTest {

    private BillingInternalEventInputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BillingInternalEventInputValidator();
    }

    @Test
    @DisplayName("Should reject null event payload")
    void testRejectNullPayload() {
        boolean isValid = validator.isValidWalletCompletedEvent(null);
        assertFalse(isValid, "Null payload should be rejected");
    }

    @Test
    @DisplayName("Should reject event with missing required fields")
    void testRejectMissingRequiredFields() {
        Map<String, Object> event = Map.of(
                "eventType", "wallet.transaction.completed"
                // Missing payload, messageId, etc.
        );
        boolean isValid = validator.isValidWalletCompletedEvent(event);
        assertFalse(isValid, "Event missing required fields should be rejected");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>alert('xss')</script>", "'; DROP TABLE --", "\\x00\\x01\\x02", ""})
    @DisplayName("Should reject event with malformed or injection-prone values")
    void testRejectMalformedValues(String maliciousValue) {
        Map<String, Object> payload = Map.of("maliciousField", maliciousValue);
        Map<String, Object> event = Map.of(
                "eventType", "wallet.transaction.completed",
                "payload", payload,
                "messageId", "valid-id-123"
        );
        boolean isValid = validator.isValidWalletCompletedEvent(event);
        assertFalse(isValid, "Malformed values should be rejected");
    }

    @Test
    @DisplayName("Should reject event with oversized payload")
    void testRejectOversizedPayload() {
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            largeString.append("x");
        }
        
        Map<String, Object> event = Map.of(
                "eventType", "wallet.transaction.completed",
                "payload", Map.of("largeField", largeString.toString()),
                "messageId", "valid-id-123"
        );
        boolean isValid = validator.isValidWalletCompletedEvent(event);
        assertFalse(isValid, "Oversized payload should be rejected");
    }

    @Test
    @DisplayName("Should reject event with invalid eventType")
    void testRejectInvalidEventType() {
        Map<String, Object> event = Map.of(
                "eventType", "unknown.event.type",
                "payload", Map.of(),
                "messageId", "valid-id-123"
        );
        boolean isValid = validator.isValidWalletCompletedEvent(event);
        assertFalse(isValid, "Unknown event type should be rejected");
    }

    @Test
    @DisplayName("Should reject event with negative monetary values")
    void testRejectNegativeMonetaryValues() {
        Map<String, Object> event = Map.of(
                "eventType", "wallet.transaction.completed",
                "payload", Map.of("amount", -100.00),
                "messageId", "valid-id-123"
        );
        boolean isValid = validator.isValidWalletCompletedEvent(event);
        assertFalse(isValid, "Negative amounts should be rejected");
    }

    @Test
    @DisplayName("Should reject event with zero payment amount")
    void testRejectZeroAmount() {
        Map<String, Object> event = Map.of(
                "eventType", "wallet.transaction.completed",
                "payload", Map.of("amount", 0.00),
                "messageId", "valid-id-123"
        );
        boolean isValid = validator.isValidWalletCompletedEvent(event);
        assertFalse(isValid, "Zero amount should be rejected");
    }

    @Test
    @DisplayName("Should accept valid well-formed event")
    void testAcceptValidEvent() {
        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", "550e8400-e29b-41d4-a716-446655440001");
        payload.put("idempotencyKey", UUID.randomUUID().toString());

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "wallet.transaction.completed");
        event.put("requestId", UUID.randomUUID().toString());
        event.put(InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD, "550e8400-e29b-41d4-a716-446655440000");
        event.put(InternalServiceMessageAuthenticator.NONCE_FIELD, UUID.randomUUID().toString());
        event.put(InternalServiceMessageAuthenticator.EXPIRES_AT_FIELD, now.plusSeconds(300).toString());
        event.put("occurredAt", now.toString());
        event.put(InternalServiceMessageAuthenticator.SOURCE_SERVICE_FIELD, "wallet-service");
        event.put(InternalServiceMessageAuthenticator.SERVICE_TOKEN_FIELD, "test-token");
        event.put("payload", payload);

        boolean isValid = validator.isValidWalletCompletedEvent(event);
        assertTrue(isValid, "Valid event should be accepted");
    }
}
