package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WalletLimitEnforcementServiceTest {

    private WalletLimitEnforcementService service;

    @BeforeEach
    void setUp() {
        service = new WalletLimitEnforcementService();
    }

    @Test
    void validateShouldRejectTransferAboveFlowLimit() {
        boolean valid = service.validate(UUID.randomUUID(), WalletLimitEnforcementService.FlowType.TRANSFER, new BigDecimal("2500.00"));

        assertThat(valid).isFalse();
    }

    @Test
    void validateShouldRejectDailyLimitAfterRecord() {
        UUID walletId = UUID.randomUUID();
        service.record(walletId, WalletLimitEnforcementService.FlowType.DEPOSIT, new BigDecimal("4900.00"));

        boolean valid = service.validate(walletId, WalletLimitEnforcementService.FlowType.DEPOSIT, new BigDecimal("200.00"));

        assertThat(valid).isFalse();
    }

    @Test
    void validateShouldAcceptWithinLimits() {
        boolean valid = service.validate(UUID.randomUUID(), WalletLimitEnforcementService.FlowType.WITHDRAWAL, new BigDecimal("100.00"));

        assertThat(valid).isTrue();
    }
}
