package com.elvo.billing.audit;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.TypedQuery;

@ExtendWith(MockitoExtension.class)
class ImmutableAuditEventStoreTest {

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @Mock
    private TypedQuery<String> query;

    private ImmutableAuditEventStore store;

    @BeforeEach
    void setUp() {
        store = new ImmutableAuditEventStore();
        ReflectionTestUtils.setField(store, "entityManager", entityManager);
    }

    @Test
    void appendShouldSetGenesisAndRecordHashForFirstRecord() {
        when(entityManager.createQuery(eq("select r.recordHash from AuditEventRecord r order by r.createdAt desc"), eq(String.class)))
                .thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.setLockMode(any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        AuditEventRecord record = new AuditEventRecord(
                "billing.payment.create",
                "req-1",
                "corr-1",
                Instant.parse("2026-04-08T12:00:00Z"),
                "status=PENDING");

        store.append(record);

        assertThat(record.getPreviousHash()).isEqualTo(AuditEventRecord.GENESIS_HASH);
        assertThat(record.getRecordHash()).isNotBlank().hasSize(64);
    }

    @Test
    void appendShouldChainToLatestHash() {
        String latestHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        when(entityManager.createQuery(eq("select r.recordHash from AuditEventRecord r order by r.createdAt desc"), eq(String.class)))
                .thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.setLockMode(any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(latestHash));

        AuditEventRecord record = new AuditEventRecord(
                "billing.event.published",
                "req-2",
                "corr-2",
                Instant.parse("2026-04-08T12:05:00Z"),
                "eventType=billing.payment.reversed");

        store.append(record);

        assertThat(record.getPreviousHash()).isEqualTo(latestHash);
        assertThat(record.getRecordHash()).isNotBlank().hasSize(64);

        ArgumentCaptor<AuditEventRecord> persistCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(entityManager).persist(persistCaptor.capture());
        assertThat(persistCaptor.getValue().getRecordHash()).isEqualTo(record.getRecordHash());
    }
}