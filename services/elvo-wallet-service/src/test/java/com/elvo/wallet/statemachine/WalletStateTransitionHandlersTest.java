package com.elvo.wallet.statemachine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.model.WalletFlowResult;

@ExtendWith(MockitoExtension.class)
class WalletStateTransitionHandlersTest {

    @Mock
    private WalletService walletService;

    @Test
    void shouldDelegateReserveFundsToReservationFlow() {
        WalletStateTransitionHandlers handlers = new WalletStateTransitionHandlers(walletService);
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(walletService.createReservation(any())).thenReturn(
                WalletFlowResult.success("reserved", walletId, UUID.randomUUID(), "wallet.transaction.reserved"));

        handlers.reserveFunds(walletId, userId, new BigDecimal("50.00"), "idem-1", "ref-1");

        verify(walletService).createReservation(any());
    }

    @Test
    void shouldDelegateCommitAndRollback() {
        WalletStateTransitionHandlers handlers = new WalletStateTransitionHandlers(walletService);
        UUID reservationId = UUID.randomUUID();
        when(walletService.confirmReservation(eq(reservationId), eq("idem-2"))).thenReturn(
                WalletFlowResult.success("committed", reservationId, reservationId, "wallet.transaction.committed"));
        when(walletService.releaseReservation(eq(reservationId), eq("idem-3"))).thenReturn(
                WalletFlowResult.success("rolled-back", reservationId, reservationId, "wallet.transaction.reversed"));

        handlers.commitFunds(reservationId, "idem-2");
        handlers.rollbackFunds(reservationId, "idem-3");

        verify(walletService).confirmReservation(reservationId, "idem-2");
        verify(walletService).releaseReservation(reservationId, "idem-3");
    }
}
