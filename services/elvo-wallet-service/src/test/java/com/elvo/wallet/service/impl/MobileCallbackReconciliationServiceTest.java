package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.elvo.wallet.security.WalletFieldEncryptionService;

class MobileCallbackReconciliationServiceTest {

    @Test
    void shouldRejectDuplicateCallbackReference() {
        WalletFieldEncryptionService encryptionService = new WalletFieldEncryptionService("callback-replay-test-key");
        MobileCallbackReconciliationService service = new MobileCallbackReconciliationService(encryptionService, 600);
        long timestamp = Instant.now().getEpochSecond();

        boolean first = service.consumeOnce("cb-dup-1", timestamp);
        boolean second = service.consumeOnce("cb-dup-1", timestamp + 1);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void shouldRejectStaleCallbackReference() {
        WalletFieldEncryptionService encryptionService = new WalletFieldEncryptionService("callback-replay-test-key");
        MobileCallbackReconciliationService service = new MobileCallbackReconciliationService(encryptionService, 120);
        long staleTimestamp = Instant.now().minusSeconds(180).getEpochSecond();

        assertThat(service.consumeOnce("cb-stale-1", staleTimestamp)).isFalse();
    }

    @Test
    void shouldStoreAndReconcilePendingCallback() {
        WalletFieldEncryptionService encryptionService = new WalletFieldEncryptionService("callback-replay-test-key");
        MobileCallbackReconciliationService service = new MobileCallbackReconciliationService(encryptionService, 600);

        service.scheduleRetry("cb-1", UUID.randomUUID(), new BigDecimal("20.00"));
        service.markReconciled("cb-1");

        assertThat(service.consumeOnce("cb-2", Instant.now().getEpochSecond())).isTrue();
    }
}
