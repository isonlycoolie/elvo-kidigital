package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        service.put("key-123", result);

        assertThat(service.get("key-123")).contains(result);
    }

    @Test
    void blankKeyShouldBeIgnored() {
        WalletFlowResult result = WalletFlowResult.failure("invalid", null, "wallet.deposit.failed");

        service.put("   ", result);

        assertThat(service.get("   ")).isEmpty();
    }
}
