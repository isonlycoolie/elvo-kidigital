package com.elvo.billing.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Security tests for sensitive data masking to prevent information leakage in logs and error messages.
 * Verifies that sensitive information is properly redacted.
 */
@DisplayName("Sensitive Data Masking Security Tests")
public class SensitiveDataMaskingSecurityTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "password=secretp@ss123",
            "token=eyJhbGciOiJIUzI1NiIs",
            "authorization: Bearer abc123xyz",
            "phone=+1234567890",
            "account=ACC-12345-67890"
    })
    @DisplayName("Should mask sensitive fields in error messages")
    void testMaskSensitiveFields(String sensitiveText) {
        String masked = SensitiveDataMasker.maskText(sensitiveText);
        assertNotNull(masked, "Masked text should not be null");
        assertTrue(masked.contains("***"), "Sensitive values should be redacted");
        assertFalse(masked.contains("secretp@ss"), "Should not contain actual password");
        assertFalse(masked.contains("Bearer abc123xyz"), "Should not contain actual token");
        assertFalse(masked.contains("eyJhbGciOiJIUzI1NiIs"), "Should not contain actual token payload");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "550e8400-e29b-41d4-a716-446655440000",
            "12345678901234567890",
            "user@example.com"
    })
    @DisplayName("Should mask sensitive identifiers")
    void testMaskIdentifiers(String identifier) {
        String masked = SensitiveDataMasker.maskIdentifier(identifier);
        assertNotNull(masked, "Masked identifier should not be null");
        if (identifier.length() > 4) {
            assertTrue(masked.contains("****"), "Identifier should contain mask");
            assertFalse(masked.equals(identifier), "Masked identifier should differ from original");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "short"})
    @DisplayName("Should handle edge cases in masking")
    void testMaskEdgeCases(String value) {
        String masked = SensitiveDataMasker.maskText(value);
        // Edge cases should be handled gracefully without throwing exceptions
        assertNotNull(masked, "Masking should not throw for edge cases");
    }
}
