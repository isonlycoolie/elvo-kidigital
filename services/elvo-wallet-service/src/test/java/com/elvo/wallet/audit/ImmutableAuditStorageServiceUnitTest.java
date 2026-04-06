package com.elvo.wallet.audit;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImmutableAuditStorageServiceUnitTest {

    @Test
    void shouldPersistSignedPayload() {
        ImmutableAuditEventStore store = org.mockito.Mockito.mock(ImmutableAuditEventStore.class);
        AuditEventSignatureService signatureService = org.mockito.Mockito.mock(AuditEventSignatureService.class);
        ImmutableAuditStorageService service = new ImmutableAuditStorageService(store, signatureService);

        Instant occurredAt = Instant.parse("2026-04-06T12:00:00Z");
        when(signatureService.sign("wallet.transfer.completed", "req-1", "corr-1", occurredAt, "{\"amount\":10.00}"))
                .thenReturn("sig-1");
        when(signatureService.attachSignature("{\"amount\":10.00}", "sig-1"))
                .thenReturn("sig=sig-1;payload={\"amount\":10.00}");

        service.append("wallet.transfer.completed", "req-1", "corr-1", occurredAt, "{\"amount\":10.00}");

        ArgumentCaptor<AuditEventRecord> captor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(store).append(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("sig=sig-1;payload={\"amount\":10.00}", captor.getValue().getPayload());
    }
}
