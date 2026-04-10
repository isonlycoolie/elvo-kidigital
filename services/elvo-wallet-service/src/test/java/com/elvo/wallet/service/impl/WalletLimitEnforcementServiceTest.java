package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.client.AccountServiceClient;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletLimitEnforcementServiceTest {

    private WalletLimitEnforcementService service;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @BeforeEach
    void setUp() {
        service = new WalletLimitEnforcementService(
                new BigDecimal("5000.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("10000.00"),
                walletRepository,
                accountServiceClient);
    }

    @Test
    void validateShouldDelegateToAccountPolicyService() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(accountServiceClient.findAccountByUserId(userId))
                .thenReturn(Optional.of(new AccountServiceClient.AccountSummary(
                        accountId,
                        userId,
                        "ELVO-000000000001",
                        "WALLET",
                        "ACTIVE",
                        "VERIFIED",
                        null,
                        null,
                        null,
                        1L)));
        when(accountServiceClient.checkLimit(any()))
                .thenReturn(new AccountServiceClient.AccountLimitCheckResult(
                        true,
                        "Allowed",
                        accountId,
                        "MAX_SINGLE_TRANSACTION",
                        new BigDecimal("2500.00")));

        boolean valid = service.validate(walletId, WalletLimitEnforcementService.FlowType.TRANSFER, new BigDecimal("2500.00"));

        assertThat(valid).isTrue();
        verify(accountServiceClient).checkLimit(any());
    }

    @Test
    void validateShouldRejectWhenAccountPolicyServiceRejects() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(accountServiceClient.findAccountByUserId(userId))
                .thenReturn(Optional.of(new AccountServiceClient.AccountSummary(
                        accountId,
                        userId,
                        "ELVO-000000000001",
                        "WALLET",
                        "ACTIVE",
                        "VERIFIED",
                        null,
                        null,
                        null,
                        1L)));
        when(accountServiceClient.checkLimit(any()))
                .thenReturn(new AccountServiceClient.AccountLimitCheckResult(
                        false,
                        "Amount exceeds limit",
                        accountId,
                        "DEPOSIT",
                        new BigDecimal("200.00")));

        boolean valid = service.validate(walletId, WalletLimitEnforcementService.FlowType.DEPOSIT, new BigDecimal("200.00"));

        assertThat(valid).isFalse();
    }

    @Test
    void validateShouldAcceptWithinLimits() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(accountServiceClient.findAccountByUserId(userId))
            .thenReturn(Optional.of(new AccountServiceClient.AccountSummary(
                accountId,
                userId,
                "ELVO-000000000001",
                "WALLET",
                "ACTIVE",
                "VERIFIED",
                null,
                null,
                null,
                1L)));
        when(accountServiceClient.checkLimit(any()))
            .thenReturn(new AccountServiceClient.AccountLimitCheckResult(
                true,
                "Allowed",
                accountId,
                "WITHDRAWAL",
                new BigDecimal("100.00")));

        boolean valid = service.validate(walletId, WalletLimitEnforcementService.FlowType.WITHDRAWAL, new BigDecimal("100.00"));

        assertThat(valid).isTrue();
    }

    @Test
    void getLimitsShouldReturnConfiguredValues() {
        WalletLimitEnforcementService configured = new WalletLimitEnforcementService(
            new BigDecimal("7000.00"),
            new BigDecimal("70000.00"),
            new BigDecimal("3000.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("12000.00"),
            null,
            null
        );

        var limits = configured.getLimits(UUID.randomUUID());

        assertThat(limits.getDailyLimit()).isEqualByComparingTo("7000.00");
        assertThat(limits.getMonthlyLimit()).isEqualByComparingTo("70000.00");
        assertThat(limits.getTransferLimit()).isEqualByComparingTo("3000.00");
        assertThat(limits.getWithdrawalLimit()).isEqualByComparingTo("1500.00");
        assertThat(limits.getDepositLimit()).isEqualByComparingTo("12000.00");
    }
}
