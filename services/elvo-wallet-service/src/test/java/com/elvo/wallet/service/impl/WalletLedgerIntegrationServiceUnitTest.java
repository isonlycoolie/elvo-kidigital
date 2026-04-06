package com.elvo.wallet.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class WalletLedgerIntegrationServiceUnitTest {

    @SuppressWarnings("unchecked")
    private WalletLedgerIntegrationService serviceWithoutRedis() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new WalletLedgerIntegrationService(provider, "elvo:wallet:ledger:hash:");
    }

    @Test
    void shouldInitializeWithGenesisHash() {
        WalletLedgerIntegrationService service = serviceWithoutRedis();

        assertEquals("GENESIS", service.latestHash(UUID.randomUUID()));
    }

    @Test
    void shouldUpdateHashChainAcrossEntries() {
        WalletLedgerIntegrationService service = serviceWithoutRedis();
        UUID walletId = UUID.randomUUID();

        String firstPrevious = service.latestHash(walletId);
        service.recordDoubleEntry("transfer", walletId, new BigDecimal("10.00"), "ref-1");
        String firstCurrent = service.latestHash(walletId);
        service.recordDoubleEntry("transfer", walletId, new BigDecimal("20.00"), "ref-2");
        String secondCurrent = service.latestHash(walletId);

        assertEquals("GENESIS", firstPrevious);
        assertNotNull(firstCurrent);
        assertNotNull(secondCurrent);
        assertNotEquals("GENESIS", firstCurrent);
        assertNotEquals(firstCurrent, secondCurrent);
    }
}
