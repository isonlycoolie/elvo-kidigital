package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.statemachine.WalletRetryMechanism;

@ExtendWith(MockitoExtension.class)
class DefaultWalletTransactionServiceTest {

    @Mock
    private WalletRetryMechanism walletRetryMechanism;

    @Test
    void shouldDelegateReserveCommitRollback() {
        DefaultWalletTransactionService service = new DefaultWalletTransactionService(walletRetryMechanism);
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        when(walletRetryMechanism.reserveWithRetry(eq(walletId), eq(userId), eq(new BigDecimal("30.00")), eq("idem-1"), eq("ref-1")))
                .thenReturn(WalletFlowResult.success("reserved", walletId, reservationId, "wallet.transaction.reserved"));
        when(walletRetryMechanism.commitWithRetry(eq(reservationId), eq("idem-2")))
                .thenReturn(WalletFlowResult.success("committed", walletId, reservationId, "wallet.transaction.committed"));
        when(walletRetryMechanism.rollbackWithRetry(eq(reservationId), eq("idem-3")))
                .thenReturn(WalletFlowResult.success("rolled-back", walletId, reservationId, "wallet.transaction.reversed"));

        WalletFlowResult reserveResult = service.reserveFunds(walletId, userId, new BigDecimal("30.00"), "idem-1", "ref-1");
        WalletFlowResult commitResult = service.commitFunds(reservationId, "idem-2");
        WalletFlowResult rollbackResult = service.rollbackFunds(reservationId, "idem-3");

        assertThat(reserveResult.success()).isTrue();
        assertThat(commitResult.success()).isTrue();
        assertThat(rollbackResult.success()).isTrue();

        verify(walletRetryMechanism).reserveWithRetry(walletId, userId, new BigDecimal("30.00"), "idem-1", "ref-1");
        verify(walletRetryMechanism).commitWithRetry(reservationId, "idem-2");
        verify(walletRetryMechanism).rollbackWithRetry(reservationId, "idem-3");
    }
}
