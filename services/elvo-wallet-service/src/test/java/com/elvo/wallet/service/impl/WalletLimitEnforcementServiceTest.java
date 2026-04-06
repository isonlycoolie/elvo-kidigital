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

    @Test
    void getLimitsShouldReturnConfiguredValues() {
        WalletLimitEnforcementService configured = new WalletLimitEnforcementService(
            new BigDecimal("7000.00"),
            new BigDecimal("70000.00"),
            new BigDecimal("3000.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("12000.00")
        );

        var limits = configured.getLimits(UUID.randomUUID());

        assertThat(limits.getDailyLimit()).isEqualByComparingTo("7000.00");
        assertThat(limits.getMonthlyLimit()).isEqualByComparingTo("70000.00");
        assertThat(limits.getTransferLimit()).isEqualByComparingTo("3000.00");
        assertThat(limits.getWithdrawalLimit()).isEqualByComparingTo("1500.00");
        assertThat(limits.getDepositLimit()).isEqualByComparingTo("12000.00");
    }
}
