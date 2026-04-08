package com.elvo.wallet.service.orchestration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.entity.Reservation;
import com.elvo.wallet.repository.ReservationRepository;
import com.elvo.wallet.security.InternalServiceMessageAuthenticator;
import com.elvo.wallet.security.WalletInternalEventInputValidator;
import com.elvo.wallet.security.WalletReservationStateTransitionValidator;
import com.elvo.wallet.service.WalletTransactionService;
import com.elvo.wallet.service.impl.InternalEventIdempotencyService;
import com.elvo.wallet.service.model.WalletFlowResult;

@ExtendWith(MockitoExtension.class)
class WalletTransactionOrchestratorTest {

    @Mock
    private WalletTransactionService walletTransactionService;

    @Mock
    private InternalEventIdempotencyService internalEventIdempotencyService;

    @Mock
    private ReservationRepository reservationRepository;

    private final WalletInternalEventInputValidator inputValidator = new WalletInternalEventInputValidator();
    private final WalletReservationStateTransitionValidator stateTransitionValidator = new WalletReservationStateTransitionValidator();

    @Test
    void shouldCommitFundsOnBillingCompletedEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService,
                inputValidator,
                reservationRepository,
                stateTransitionValidator);
        UUID reservationId = UUID.randomUUID();

        when(walletTransactionService.commitFunds(eq(reservationId), eq("idem-1"))).thenReturn(
                WalletFlowResult.success("committed", reservationId, reservationId, "wallet.transaction.committed"));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any())).thenReturn(true);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(createdReservation()));

        orchestrator.onBillingCompleted(signedEvent("billing.transaction.completed", reservationId, "idem-1"));

        verify(walletTransactionService).commitFunds(eq(reservationId), eq("idem-1"));
    }

    @Test
    void shouldRollbackFundsOnBillingReversedEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService,
                inputValidator,
                reservationRepository,
                stateTransitionValidator);
        UUID reservationId = UUID.randomUUID();

        when(walletTransactionService.rollbackFunds(eq(reservationId), eq("idem-2"))).thenReturn(
                WalletFlowResult.success("rolled-back", reservationId, reservationId, "wallet.transaction.reversed"));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any())).thenReturn(true);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(createdReservation()));

        orchestrator.onBillingReversed(signedEvent("billing.transaction.reversed", reservationId, "idem-2"));

        verify(walletTransactionService).rollbackFunds(eq(reservationId), eq("idem-2"));
    }

    @Test
    void shouldRejectUnsignedBillingEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService,
                inputValidator,
                reservationRepository,
                stateTransitionValidator);

        orchestrator.onBillingCompleted(Map.of(
                "sourceService", "elvo-billing-service",
                "payload", Map.of("reservationId", UUID.randomUUID().toString(), "idempotencyKey", "idem-3")));

        verify(walletTransactionService, never()).commitFunds(any(), any());
    }

    @Test
    void shouldIgnoreDuplicateBillingCompletedEvent() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService,
                inputValidator,
                reservationRepository,
                stateTransitionValidator);
        UUID reservationId = UUID.randomUUID();

        when(walletTransactionService.commitFunds(eq(reservationId), eq("idem-dup"))).thenReturn(
                WalletFlowResult.success("committed", reservationId, reservationId, "wallet.transaction.committed"));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any()))
                .thenReturn(true)
                .thenReturn(false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(createdReservation()));

        Map<String, Object> event = signedEvent("billing.transaction.completed", reservationId, "idem-dup");
        orchestrator.onBillingCompleted(event);
        orchestrator.onBillingCompleted(event);

        verify(walletTransactionService).commitFunds(eq(reservationId), eq("idem-dup"));
    }

    @Test
    void shouldRejectBillingEventWithUnknownPayloadField() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService,
                inputValidator,
                reservationRepository,
                stateTransitionValidator);
        UUID reservationId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservationId", reservationId.toString());
        payload.put("idempotencyKey", "idem-bad");
        payload.put("unexpected", "not-allowed");
        Instant occurredAt = Instant.now();

        Map<String, Object> event = InternalServiceMessageAuthenticator.signEvent(
                "elvo-billing-service",
                Map.of(
                        "eventType", "billing.transaction.completed",
                        "version", "v1",
                        "requestId", "req-invalid",
                        "messageId", UUID.randomUUID().toString(),
                        "nonce", UUID.randomUUID().toString(),
                        "occurredAt", occurredAt.toString(),
                        "expiresAt", occurredAt.plus(5, ChronoUnit.MINUTES).toString(),
                        "correlationId", "corr-invalid",
                        "payload", payload));

        orchestrator.onBillingCompleted(event);

        verify(walletTransactionService, never()).commitFunds(any(), any());
    }

    @Test
    void shouldRejectCommitWhenReservationAlreadyReleased() {
        WalletTransactionOrchestrator orchestrator = new WalletTransactionOrchestrator(
                walletTransactionService,
                internalEventIdempotencyService,
                inputValidator,
                reservationRepository,
                stateTransitionValidator);
        UUID reservationId = UUID.randomUUID();

        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any())).thenReturn(true);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(releasedReservation()));

        orchestrator.onBillingCompleted(signedEvent("billing.transaction.completed", reservationId, "idem-state"));

        verify(walletTransactionService, never()).commitFunds(any(), any());
    }

    private Reservation createdReservation() {
        Reservation reservation = new Reservation();
        reservation.setStatus(Reservation.ReservationStatus.CREATED);
        return reservation;
    }

    private Reservation releasedReservation() {
        Reservation reservation = new Reservation();
        reservation.setStatus(Reservation.ReservationStatus.RELEASED);
        return reservation;
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
