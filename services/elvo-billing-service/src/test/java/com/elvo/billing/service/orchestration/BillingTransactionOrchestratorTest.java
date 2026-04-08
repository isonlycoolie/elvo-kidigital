package com.elvo.billing.service.orchestration;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.service.BillingTransactionService;
import com.elvo.billing.service.impl.InternalEventIdempotencyService;
import com.elvo.billing.security.BillingInternalEventInputValidator;
import com.elvo.billing.security.BillingPaymentStateTransitionValidator;
import com.elvo.billing.security.BillingServiceAuthorizationMatrix;
import com.elvo.billing.security.BillingServiceAuthorizationProperties;
import com.elvo.billing.security.InternalServiceMessageAuthenticator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingTransactionOrchestratorTest {

    @Mock
    private BillPaymentRepository billPaymentRepository;

    @Mock
    private BillingTransactionService billingTransactionService;

    @Mock
    private InternalEventIdempotencyService internalEventIdempotencyService;

    private final BillingInternalEventInputValidator inputValidator = new BillingInternalEventInputValidator();
    private final BillingPaymentStateTransitionValidator stateTransitionValidator = new BillingPaymentStateTransitionValidator();

    @Test
    void shouldMarkPaymentSuccessWhenWalletCompletedEventIsReceived() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
            billingTransactionService,
            new BillingServiceAuthorizationMatrix(new BillingServiceAuthorizationProperties()),
            internalEventIdempotencyService,
        inputValidator,
        stateTransitionValidator);

        UUID paymentId = UUID.randomUUID();
        BillPayment payment = new BillPayment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PROCESSING);

        when(billPaymentRepository.getPaymentByIdWithLock(paymentId)).thenReturn(Optional.of(payment));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any())).thenReturn(true);

        orchestrator.onWalletCompleted(signedEvent("wallet.transaction.completed", paymentId));

        verify(billingTransactionService).completeTransaction(payment);
        verify(billPaymentRepository).updatePaymentStatus(paymentId, PaymentStatus.SUCCESS);
    }

    @Test
    void shouldTriggerCompensationWhenWalletFailedEventIsReceived() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
            billingTransactionService,
            new BillingServiceAuthorizationMatrix(new BillingServiceAuthorizationProperties()),
            internalEventIdempotencyService,
        inputValidator,
        stateTransitionValidator);

        UUID paymentId = UUID.randomUUID();
        BillPayment payment = new BillPayment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PROCESSING);

        when(billPaymentRepository.getPaymentByIdWithLock(paymentId)).thenReturn(Optional.of(payment));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any())).thenReturn(true);

        orchestrator.onWalletFailed(signedEvent("wallet.transaction.failed", paymentId, "wallet rejection"));

        verify(billingTransactionService).reverseTransaction(payment, "wallet rejection");
        verify(billPaymentRepository, never()).updatePaymentStatus(eq(paymentId), any());
    }

    @Test
    void shouldSkipWalletEventWithoutPaymentId() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
            billingTransactionService,
            new BillingServiceAuthorizationMatrix(new BillingServiceAuthorizationProperties()),
            internalEventIdempotencyService,
        inputValidator,
        stateTransitionValidator);

        orchestrator.onWalletCompleted(Map.of("payload", Map.of("otherKey", "value")));

        verify(billingTransactionService, never()).completeTransaction(any());
        verify(billPaymentRepository, never()).updatePaymentStatus(any(), any());
    }

    @Test
    void shouldRejectUnsignedWalletEvent() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
            billingTransactionService,
            new BillingServiceAuthorizationMatrix(new BillingServiceAuthorizationProperties()),
            internalEventIdempotencyService,
        inputValidator,
        stateTransitionValidator);

        orchestrator.onWalletCompleted(Map.of(
                "sourceService", "elvo-wallet-service",
                "payload", Map.of("paymentId", UUID.randomUUID().toString())));

        verify(billingTransactionService, never()).completeTransaction(any());
        verify(billPaymentRepository, never()).updatePaymentStatus(any(), any());
    }

    @Test
    void shouldIgnoreDuplicateWalletCompletedEvent() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
                billingTransactionService,
                new BillingServiceAuthorizationMatrix(new BillingServiceAuthorizationProperties()),
            internalEventIdempotencyService,
        inputValidator,
        stateTransitionValidator);

        UUID paymentId = UUID.randomUUID();
        BillPayment payment = new BillPayment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.PROCESSING);

        when(billPaymentRepository.getPaymentByIdWithLock(paymentId)).thenReturn(Optional.of(payment));
        when(internalEventIdempotencyService.markIfFirstProcessed(any(), any(), any(), any()))
                .thenReturn(true)
                .thenReturn(false);

        Map<String, Object> event = signedEvent("wallet.transaction.completed", paymentId);
        orchestrator.onWalletCompleted(event);
        orchestrator.onWalletCompleted(event);

        verify(billingTransactionService).completeTransaction(payment);
        verify(billPaymentRepository).updatePaymentStatus(paymentId, PaymentStatus.SUCCESS);
    }

    private Map<String, Object> signedEvent(String eventType, UUID paymentId) {
        return signedEvent(eventType, paymentId, null);
    }

    private Map<String, Object> signedEvent(String eventType, UUID paymentId, String reason) {
        Instant occurredAt = Instant.now();
        Map<String, Object> payload = reason == null
            ? Map.of("paymentId", paymentId.toString(), "idempotencyKey", paymentId.toString())
            : Map.of("paymentId", paymentId.toString(), "idempotencyKey", paymentId.toString(), "reason", reason);
        return InternalServiceMessageAuthenticator.signEvent(
                "elvo-wallet-service",
                Map.of(
                        "eventType", eventType,
                        "eventVersion", "v1",
                        "requestId", "req-1",
                        "messageId", UUID.randomUUID().toString(),
                        "nonce", UUID.randomUUID().toString(),
                        "occurredAt", occurredAt.toString(),
                        "expiresAt", occurredAt.plus(5, ChronoUnit.MINUTES).toString(),
                        "payload", payload));
    }

    @Test
    void shouldRejectWalletEventWithUnknownPayloadField() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
                billingTransactionService,
                new BillingServiceAuthorizationMatrix(new BillingServiceAuthorizationProperties()),
                internalEventIdempotencyService,
        inputValidator,
        stateTransitionValidator);

        UUID paymentId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", paymentId.toString());
        payload.put("idempotencyKey", "idem-unknown");
        payload.put("unexpected", "bad-value");
        Instant occurredAt = Instant.now();
        Map<String, Object> event = InternalServiceMessageAuthenticator.signEvent(
                "elvo-wallet-service",
                Map.of(
                        "eventType", "wallet.transaction.completed",
                        "eventVersion", "v1",
                        "requestId", "req-2",
                        "messageId", UUID.randomUUID().toString(),
                        "nonce", UUID.randomUUID().toString(),
                        "occurredAt", occurredAt.toString(),
                        "expiresAt", occurredAt.plus(5, ChronoUnit.MINUTES).toString(),
                        "payload", payload));

        orchestrator.onWalletCompleted(event);

        verify(billingTransactionService, never()).completeTransaction(any());
        verify(billPaymentRepository, never()).updatePaymentStatus(any(), any());
    }
}
