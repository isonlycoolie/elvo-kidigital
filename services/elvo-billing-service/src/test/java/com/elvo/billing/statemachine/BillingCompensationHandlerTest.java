package com.elvo.billing.statemachine;

import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.service.event.BillingEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BillingCompensationHandlerTest {

    @Mock
    private BillingStateTransitionHandlers stateTransitionHandlers;

    @Mock
    private BillingEventPublisher billingEventPublisher;

    @Test
    void shouldPersistReversalAndPublishCompletionEvent() {
        BillingCompensationHandler compensationHandler = new BillingCompensationHandler(
                stateTransitionHandlers,
                billingEventPublisher);

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId("req-1");
        payment.setStatus(PaymentStatus.FAILED);
        payment.setAmount(new BigDecimal("1200.00"));
        payment.setCurrency("TZS");

        PaymentResponseDto response = compensationHandler.compensateFailedTransaction(payment, "wallet reservation released");

        verify(stateTransitionHandlers).handleDatabaseUpdate(
                eq(payment),
                eq(PaymentStatus.FAILED),
                eq(PaymentStatus.REVERSED),
                eq("PAYMENT_COMPENSATED"),
                eq("wallet reservation released"),
                eq("{\"reason\":\"wallet reservation released\",\"status\":\"REVERSED\"}"));
        verify(billingEventPublisher).publishTransactionCompleted(
                eq("req-1"),
                eq("{\"reason\":\"wallet reservation released\",\"status\":\"REVERSED\"}"));

        assertEquals(PaymentStatus.REVERSED, response.getStatus());
        assertEquals(new BigDecimal("1200.00"), response.getPaidAmount());
    }

    @Test
    void shouldReturnWithoutUpdateWhenPaymentAlreadyReversed() {
        BillingCompensationHandler compensationHandler = new BillingCompensationHandler(
                stateTransitionHandlers,
                billingEventPublisher);

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId("req-2");
        payment.setStatus(PaymentStatus.REVERSED);

        PaymentResponseDto response = compensationHandler.compensateFailedTransaction(payment, "already done");

        verify(stateTransitionHandlers, never()).handleDatabaseUpdate(
                eq(payment),
                eq(PaymentStatus.REVERSED),
                eq(PaymentStatus.REVERSED),
                eq("PAYMENT_COMPENSATED"),
                eq("already done"),
                eq("{\"reason\":\"already done\",\"status\":\"REVERSED\"}"));
        verify(billingEventPublisher, never()).publishTransactionCompleted(eq("req-2"), eq("{}"));
        assertEquals("payment already reversed", response.getMessage());
    }

    @Test
    void shouldRejectNullPayment() {
        BillingCompensationHandler compensationHandler = new BillingCompensationHandler(
                stateTransitionHandlers,
                billingEventPublisher);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> compensationHandler.compensateFailedTransaction(null, "reason"));

        assertEquals("payment is required for compensation", ex.getMessage());
    }
}
