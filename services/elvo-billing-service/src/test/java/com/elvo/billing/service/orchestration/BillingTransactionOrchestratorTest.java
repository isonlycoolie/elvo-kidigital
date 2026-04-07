package com.elvo.billing.service.orchestration;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.service.BillingTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    @Test
    void shouldMarkPaymentSuccessWhenWalletCompletedEventIsReceived() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
                billingTransactionService);

        UUID paymentId = UUID.randomUUID();
        BillPayment payment = new BillPayment();
        payment.setPaymentId(paymentId);

        when(billPaymentRepository.getPaymentById(paymentId)).thenReturn(Optional.of(payment));

        orchestrator.onWalletCompleted(Map.of(
                "payload", Map.of("paymentId", paymentId.toString())));

        verify(billingTransactionService).completeTransaction(payment);
        verify(billPaymentRepository).updatePaymentStatus(paymentId, PaymentStatus.SUCCESS);
    }

    @Test
    void shouldTriggerCompensationWhenWalletFailedEventIsReceived() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
                billingTransactionService);

        UUID paymentId = UUID.randomUUID();
        BillPayment payment = new BillPayment();
        payment.setPaymentId(paymentId);

        when(billPaymentRepository.getPaymentById(paymentId)).thenReturn(Optional.of(payment));

        orchestrator.onWalletFailed(Map.of(
                "payload", Map.of("paymentId", paymentId.toString(), "reason", "wallet rejection")));

        verify(billingTransactionService).reverseTransaction(payment, "wallet rejection");
        verify(billPaymentRepository, never()).updatePaymentStatus(eq(paymentId), any());
    }

    @Test
    void shouldSkipWalletEventWithoutPaymentId() {
        BillingTransactionOrchestrator orchestrator = new BillingTransactionOrchestrator(
                billPaymentRepository,
                billingTransactionService);

        orchestrator.onWalletCompleted(Map.of("payload", Map.of("otherKey", "value")));

        verify(billingTransactionService, never()).completeTransaction(any());
        verify(billPaymentRepository, never()).updatePaymentStatus(any(), any());
    }
}
