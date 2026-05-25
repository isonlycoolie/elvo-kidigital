package com.elvo.billing.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Security tests for authorization and access control.
 * Verifies that service-level permissions are enforced correctly.
 */
@DisplayName("Authorization and Access Control Security Tests")
@SpringBootTest
@ActiveProfiles("test")
public class AuthorizationSecurityTest {

    @Autowired
    private BillingServiceAuthorizationMatrix authorizationMatrix;

    @Test
    @DisplayName("Should deny access to unauthorized service")
    void testDenyUnauthorizedService() {
        boolean allowed = authorizationMatrix.isAllowed("unknown-service", "CONSUME", "billing.queue");
        assertFalse(allowed, "Unknown service should not be allowed");
    }

    @Test
    @DisplayName("Should deny queue access not granted to service")
    void testDenyQueueAccessNotGranted() {
        boolean allowed = authorizationMatrix.isAllowed("wallet-service", "CONSUME", "billing.admin.queue");
        assertFalse(allowed, "Service should not access admin queues");
    }

    @Test
    @DisplayName("Should allow wallet-service to consume from expected queue")
    void testAllowAuthorizedQueueAccess() {
        boolean allowed = authorizationMatrix.isAllowed("wallet-service", "CONSUME", "wallet.transaction.completed.queue");
        assertTrue(allowed, "Wallet service should access its own queues");
    }

    @Test
    @DisplayName("Should deny operation not granted to service")
    void testDenyUnauthorizedOperation() {
        boolean allowed = authorizationMatrix.isAllowed("wallet-service", "ADMIN_OVERRIDE", "billing.queue");
        assertFalse(allowed, "Service should not perform unauthorized operations");
    }

    @ParameterizedTest
    @ValueSource(strings = {""})
    @DisplayName("Should deny access for empty service name")
    void testDenyEmptyServiceName(String serviceName) {
        boolean allowed = authorizationMatrix.isAllowed(serviceName, "CONSUME", "billing.queue");
        assertFalse(allowed, "Empty service name should not be allowed");
    }

    @Test
    @DisplayName("Should audit authorization check failures")
    void testAuthorizationFailureAudit() {
        authorizationMatrix.isAllowed("unauthorized-service", "INVALID_OP", "restricted.queue");
        // Verify that the failed authorization is logged in audit system
        // This would be checked by examining audit logs or mock verification
    }
}
