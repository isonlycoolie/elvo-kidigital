package com.elvo.wallet.service.orchestration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.service.WalletTransactionService;
import com.elvo.wallet.service.impl.InternalEventIdempotencyService;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.security.InternalServiceMessageAuthenticator;

@ExtendWith(MockitoExtension.class)
class WalletTransactionOrchestratorTest {

    @Mock
    private WalletTransactionService walletTransactionService;

        @Mock
        private InternalEventIdempotencyService internalEventIdempotencyService;

    @Test
    void shouldCommitFundsOnBillingCompletedEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService);
        UUID reservationId = UUID.randomUUID();

        when(walletTransactionService.commitFunds(eq(reservationId), eq("idem-1"))).thenReturn(
                WalletFlowResult.success("committed", reservationId, reservationId, "wallet.transaction.committed"));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any())).thenReturn(true);

        orchestrator.onBillingCompleted(signedEvent("billing.transaction.completed", reservationId, "idem-1"));

        verify(walletTransactionService).commitFunds(eq(reservationId), eq("idem-1"));
    }

    @Test
    void shouldRollbackFundsOnBillingReversedEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService);
        UUID reservationId = UUID.randomUUID();

        when(walletTransactionService.rollbackFunds(eq(reservationId), eq("idem-2"))).thenReturn(
                WalletFlowResult.success("rolled-back", reservationId, reservationId, "wallet.transaction.reversed"));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any())).thenReturn(true);

        orchestrator.onBillingReversed(signedEvent("billing.transaction.reversed", reservationId, "idem-2"));

        verify(walletTransactionService).rollbackFunds(eq(reservationId), eq("idem-2"));
    }

    @Test
    void shouldRejectUnsignedBillingEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService);

        orchestrator.onBillingCompleted(Map.of(
                "sourceService", "elvo-billing-service",
                "payload", Map.of("reservationId", UUID.randomUUID().toString(), "idempotencyKey", "idem-3")));

        verify(walletTransactionService, org.mockito.Mockito.never()).commitFunds(any(), any());
    }

    @Test
    void shouldIgnoreDuplicateBillingCompletedEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService);
        UUID reservationId = UUID.randomUUID();

        when(walletTransactionService.commitFunds(eq(reservationId), eq("idem-dup"))).thenReturn(
                WalletFlowResult.success("committed", reservationId, reservationId, "wallet.transaction.committed"));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any()))
                .thenReturn(true)
                .thenReturn(false);

        Map<String, Object> event = signedEvent("billing.transaction.completed", reservationId, "idem-dup");
        orchestrator.onBillingCompleted(event);
        orchestrator.onBillingCompleted(event);

        verify(walletTransactionService).commitFunds(eq(reservationId), eq("idem-dup"));
    }

    private Map<String, Object> signedEvent(String eventType, UUID reservationId, String idempotencyKey) {
        Instant occurredAt = Instant.now();
        return InternalServiceMessageAuthenticator.signEvent(
                "elvo-billing-service",
                Map.of(
                        "eventType", eventType,
                        "version", "v1",
                        "requestId", "req-1",
                "messageId", UUID.randomUUID().toString(),
                "nonce", UUID.randomUUID().toString(),
                "occurredAt", occurredAt.toString(),
                "expiresAt", occurredAt.plus(5, ChronoUnit.MINUTES).toString(),
                        "correlationId", "corr-1",
                        "payload", Map.of(
                                "reservationId", reservationId.toString(),
                                "idempotencyKey", idempotencyKey)));
    }
}
