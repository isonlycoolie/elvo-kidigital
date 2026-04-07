package com.elvo.wallet.service.orchestration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.service.WalletTransactionService;
import com.elvo.wallet.service.model.WalletFlowResult;

@ExtendWith(MockitoExtension.class)
class WalletTransactionOrchestratorTest {

    @Mock
    private WalletTransactionService walletTransactionService;

    @Test
    void shouldCommitFundsOnBillingCompletedEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(walletTransactionService);
        UUID reservationId = UUID.randomUUID();

        when(walletTransactionService.commitFunds(eq(reservationId), eq("idem-1"))).thenReturn(
                WalletFlowResult.success("committed", reservationId, reservationId, "wallet.transaction.committed"));

        orchestrator.onBillingCompleted(Map.of("payload", Map.of(
                "reservationId", reservationId.toString(),
                "idempotencyKey", "idem-1")));

        verify(walletTransactionService).commitFunds(eq(reservationId), eq("idem-1"));
    }

    @Test
    void shouldRollbackFundsOnBillingReversedEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(walletTransactionService);
        UUID reservationId = UUID.randomUUID();

        when(walletTransactionService.rollbackFunds(eq(reservationId), eq("idem-2"))).thenReturn(
                WalletFlowResult.success("rolled-back", reservationId, reservationId, "wallet.transaction.reversed"));

        orchestrator.onBillingReversed(Map.of("payload", Map.of(
                "reservationId", reservationId.toString(),
                "idempotencyKey", "idem-2")));

        verify(walletTransactionService).rollbackFunds(eq(reservationId), eq("idem-2"));
    }
}
