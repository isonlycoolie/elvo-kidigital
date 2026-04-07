package com.elvo.billing.service.impl;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.statemachine.BillingCompensationHandler;
import com.elvo.billing.statemachine.BillingRetryMechanism;
import com.elvo.billing.statemachine.BillingStateTransitionHandlers;
import com.elvo.billing.statemachine.BillingTransactionStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultBillingTransactionServiceTest {

    @Mock
    private BillingTransactionStateMachine stateMachine;

    @Mock
    private BillingStateTransitionHandlers stateTransitionHandlers;

    @Mock
    private BillingRetryMechanism retryMechanism;

    @Mock
    private BillingCompensationHandler compensationHandler;

    @Test
    void shouldInitiateTransaction() {
        DefaultBillingTransactionService service = new DefaultBillingTransactionService(
                stateMachine,
                stateTransitionHandlers,
                retryMechanism,
                compensationHandler);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        when(stateMachine.transition(PaymentStatus.INITIATED, PaymentStatus.PENDING)).thenReturn(PaymentStatus.PENDING);

        PaymentResponseDto response = service.initiateTransaction(request, BillCategory.ELECTRICITY);

        verify(stateTransitionHandlers).handleValidation(eq(request), eq(BillCategory.ELECTRICITY));
        verify(stateMachine).transition(PaymentStatus.INITIATED, PaymentStatus.PENDING);
        assertEquals(PaymentStatus.PENDING, response.getStatus());
    }

    @Test
    void shouldProcessTransactionUsingRetryMechanism() {
        DefaultBillingTransactionService service = new DefaultBillingTransactionService(
                stateMachine,
                stateTransitionHandlers,
                retryMechanism,
                compensationHandler);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        PaymentResponseDto response = new PaymentResponseDto();
        response.setStatus(PaymentStatus.SUCCESS);
        when(retryMechanism.executePaymentWithRetry("LUKU", request)).thenReturn(response);

        PaymentResponseDto result = service.processTransaction("LUKU", request);

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        verify(retryMechanism).executePaymentWithRetry("LUKU", request);
    }

    @Test
    void shouldCompleteTransaction() {
        DefaultBillingTransactionService service = new DefaultBillingTransactionService(
                stateMachine,
                stateTransitionHandlers,
                retryMechanism,
                compensationHandler);

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("TZS");
        when(stateMachine.transition(PaymentStatus.PROCESSING, PaymentStatus.SUCCESS)).thenReturn(PaymentStatus.SUCCESS);

        PaymentResponseDto result = service.completeTransaction(payment);

        verify(stateMachine).transition(PaymentStatus.PROCESSING, PaymentStatus.SUCCESS);
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals(new BigDecimal("500.00"), result.getPaidAmount());
    }

    @Test
    void shouldReverseTransactionThroughCompensation() {
        DefaultBillingTransactionService service = new DefaultBillingTransactionService(
                stateMachine,
                stateTransitionHandlers,
                retryMechanism,
                compensationHandler);

        BillPayment payment = new BillPayment();
        PaymentResponseDto reversed = new PaymentResponseDto();
        reversed.setStatus(PaymentStatus.REVERSED);
        when(compensationHandler.compensateFailedTransaction(payment, "failure")).thenReturn(reversed);

        PaymentResponseDto result = service.reverseTransaction(payment, "failure");

        verify(compensationHandler).compensateFailedTransaction(payment, "failure");
        assertEquals(PaymentStatus.REVERSED, result.getStatus());
    }
}
