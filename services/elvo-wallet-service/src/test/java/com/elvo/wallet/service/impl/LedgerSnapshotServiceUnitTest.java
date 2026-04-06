package com.elvo.wallet.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class LedgerSnapshotServiceUnitTest {

    @SuppressWarnings("unchecked")
    private WalletLedgerIntegrationService ledgerService() {
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return new WalletLedgerIntegrationService(provider, "elvo:wallet:ledger:hash:");
    }

    @Test
    void shouldCreateDeterministicSnapshotForSameInput() {
        WalletLedgerIntegrationService ledger = ledgerService();
        LedgerSnapshotService snapshotService = new LedgerSnapshotService(ledger);
        UUID walletId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        ledger.recordDoubleEntry("transfer", walletId, new BigDecimal("10.00"), "ref-1");

        LedgerSnapshotService.LedgerSnapshot first = snapshotService.createDailySnapshot(LocalDate.of(2026, 4, 6));
        LedgerSnapshotService.LedgerSnapshot second = snapshotService.createDailySnapshot(LocalDate.of(2026, 4, 6));

        assertEquals(1, first.entryCount());
        assertEquals(first.rootHash(), second.rootHash());
        assertEquals(first.reconciliationProof(), second.reconciliationProof());
    }

    @Test
    void shouldCreateSnapshotWithNoEntries() {
        LedgerSnapshotService snapshotService = new LedgerSnapshotService(ledgerService());

        LedgerSnapshotService.LedgerSnapshot snapshot = snapshotService.createDailySnapshot(LocalDate.of(2026, 4, 7));

        assertEquals(0, snapshot.entryCount());
        assertNotNull(snapshot.rootHash());
        assertNotNull(snapshot.reconciliationProof());
    }
}
