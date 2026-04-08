package com.elvo.billing.audit;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;

class ImmutableAuditStorageServiceUnitTest {

    @Test
    void appendShouldPersistAuditEventRecord() {
        ImmutableAuditEventStore store = org.mockito.Mockito.mock(ImmutableAuditEventStore.class);
        ImmutableAuditStorageService service = new ImmutableAuditStorageService(store);

        Instant occurredAt = Instant.parse("2026-04-08T12:00:00Z");
        service.append("billing.payment.reverse", "req-1", "corr-1", occurredAt, "status=REVERSED");

        ArgumentCaptor<AuditEventRecord> captor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(store).append(captor.capture());

        AuditEventRecord record = captor.getValue();
        assertThat(record.getEventType()).isEqualTo("billing.payment.reverse");
        assertThat(record.getRequestId()).isEqualTo("req-1");
        assertThat(record.getCorrelationId()).isEqualTo("corr-1");
        assertThat(record.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(record.getPayload()).isEqualTo("status=REVERSED");
    }
}