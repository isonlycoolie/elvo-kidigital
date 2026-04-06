package com.elvo.wallet.service.impl;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.elvo.wallet.exception.IdempotencyReplayDetectedException;
import com.elvo.wallet.service.model.WalletFlowResult;

class WalletIdempotencyServiceTest {

    private WalletIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new WalletIdempotencyService();
    }

    @Test
    void putShouldReturnCachedResultForSameKey() {
        WalletFlowResult result = WalletFlowResult.success("ok", UUID.randomUUID(), UUID.randomUUID(), "wallet.deposit.completed");

        service.put("key-123", "user-1", "wallet.deposit.process", "payload-hash", result);

        assertThat(service.get("key-123", "user-1", "wallet.deposit.process", "payload-hash")).contains(result);
    }

    @Test
    void blankKeyShouldBeIgnored() {
        WalletFlowResult result = WalletFlowResult.failure("invalid", null, "wallet.deposit.failed");

        service.put("   ", result);

        assertThat(service.get("   ")).isEmpty();
    }

    @Test
    void keyReuseWithDifferentPayloadShouldBeRejected() {
        WalletFlowResult result = WalletFlowResult.success("ok", UUID.randomUUID(), UUID.randomUUID(), "wallet.deposit.completed");
        service.put("key-123", "user-1", "wallet.deposit.process", "payload-hash-1", result);

        assertThatThrownBy(() -> service.get("key-123", "user-1", "wallet.deposit.process", "payload-hash-2"))
            .isInstanceOf(IdempotencyReplayDetectedException.class)
            .hasMessageContaining("replay attempt detected");
        }

        @Test
        void compromisedKeyShouldBeBlockedAfterReplayAttempt() {
        WalletFlowResult result = WalletFlowResult.success("ok", UUID.randomUUID(), UUID.randomUUID(), "wallet.deposit.completed");
        service.put("key-123", "user-1", "wallet.deposit.process", "payload-hash-1", result);

        assertThatThrownBy(() -> service.get("key-123", "user-2", "wallet.deposit.process", "payload-hash-1"))
            .isInstanceOf(IdempotencyReplayDetectedException.class);

        assertThatThrownBy(() -> service.get("key-123", "user-1", "wallet.deposit.process", "payload-hash-1"))
            .isInstanceOf(IdempotencyReplayDetectedException.class)
            .hasMessageContaining("blocked");
    }
}
