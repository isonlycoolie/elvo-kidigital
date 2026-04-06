package com.elvo.wallet.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import com.elvo.wallet.security.SecretManagerService;

class AuditEventSignatureServiceUnitTest {

    private AuditEventSignatureService service() {
        Environment environment = org.mockito.Mockito.mock(Environment.class);
        when(environment.getProperty("elvo.secret-manager.secrets.wallet-audit-signature-secret"))
                .thenReturn("test-audit-signature-secret-1234567890");
        SecretManagerService secretManagerService = new SecretManagerService(environment);
        return new AuditEventSignatureService(secretManagerService, "sm://wallet-audit-signature-secret");
    }

    @Test
    void shouldProduceStableSignatureForSamePayload() {
        AuditEventSignatureService service = service();
        Instant occurredAt = Instant.parse("2026-04-06T12:00:00Z");

        String first = service.sign("wallet.transfer.completed", "req-1", "corr-1", occurredAt, "{\"amount\":10.00}");
        String second = service.sign("wallet.transfer.completed", "req-1", "corr-1", occurredAt, "{\"amount\":10.00}");

        assertEquals(first, second);
    }

    @Test
    void shouldChangeSignatureWhenPayloadChanges() {
        AuditEventSignatureService service = service();
        Instant occurredAt = Instant.parse("2026-04-06T12:00:00Z");

        String first = service.sign("wallet.transfer.completed", "req-1", "corr-1", occurredAt, "{\"amount\":10.00}");
        String second = service.sign("wallet.transfer.completed", "req-1", "corr-1", occurredAt, "{\"amount\":20.00}");

        assertNotEquals(first, second);
    }

    @Test
    void shouldAttachSignatureToPayload() {
        AuditEventSignatureService service = service();
        String signedPayload = service.attachSignature("{\"amount\":10.00}", "abc123");

        assertTrue(signedPayload.startsWith("sig=abc123;payload="));
    }
}
