package com.elvo.billing.statemachine;

import com.elvo.billing.client.BillingAdapter;
import com.elvo.billing.client.ProviderResolver;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.PaymentHistory;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.repository.PaymentHistoryRepository;
import com.elvo.billing.validator.UtilityPaymentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingStateTransitionHandlersTest {

    @Mock
    private UtilityPaymentValidator utilityPaymentValidator;

    @Mock
    private ProviderResolver providerResolver;

    @Mock
    private BillPaymentRepository billPaymentRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private BillingAdapter billingAdapter;

    @Test
    void shouldDelegateValidation() {
        BillingStateTransitionHandlers handlers = new BillingStateTransitionHandlers(
                utilityPaymentValidator,
                providerResolver,
                billPaymentRepository,
                paymentHistoryRepository);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("ref-1");
        request.setAmount(new BigDecimal("1000.00"));

        handlers.handleValidation(request, BillCategory.ELECTRICITY);

        verify(utilityPaymentValidator).validateForPayment(eq(request), eq(BillCategory.ELECTRICITY));
    }

    @Test
    void shouldDelegateWalletCallToResolvedProviderAdapter() {
        BillingStateTransitionHandlers handlers = new BillingStateTransitionHandlers(
                utilityPaymentValidator,
                providerResolver,
                billPaymentRepository,
                paymentHistoryRepository);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        PaymentResponseDto response = new PaymentResponseDto();
        when(providerResolver.resolve("LUKU")).thenReturn(billingAdapter);
        when(billingAdapter.pay(request)).thenReturn(response);

        PaymentResponseDto result = handlers.handleWalletCall("LUKU", request);

        assertEquals(response, result);
        verify(providerResolver).resolve("LUKU");
        verify(billingAdapter).pay(request);
    }

    @Test
    void shouldPersistPaymentAndHistoryOnDatabaseUpdate() {
        BillingStateTransitionHandlers handlers = new BillingStateTransitionHandlers(
                utilityPaymentValidator,
                providerResolver,
                billPaymentRepository,
                paymentHistoryRepository);

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId("req-1");
        payment.setCorrelationId("corr-1");

        handlers.handleDatabaseUpdate(
                payment,
                PaymentStatus.PENDING,
                PaymentStatus.PROCESSING,
                "PAYMENT_PROCESSING",
                "payment is now processing",
                "{\"step\":\"processing\"}");

        verify(billPaymentRepository).save(payment);

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(historyCaptor.capture());

        PaymentHistory history = historyCaptor.getValue();
        assertEquals(payment.getPaymentId(), history.getPaymentId());
        assertEquals("req-1", history.getRequestId());
        assertEquals("corr-1", history.getCorrelationId());
        assertEquals("PAYMENT_PROCESSING", history.getEventType());
        assertEquals("PENDING", history.getFromStatus());
        assertEquals("PROCESSING", history.getToStatus());
        assertEquals("PROCESSING", history.getResponseCode());
        assertEquals("payment is now processing", history.getResponseMessage());
        assertEquals("{\"step\":\"processing\"}", history.getMetadata());
    }

    @Test
    void shouldDefaultMetadataWhenNotProvided() {
        BillingStateTransitionHandlers handlers = new BillingStateTransitionHandlers(
                utilityPaymentValidator,
                providerResolver,
                billPaymentRepository,
                paymentHistoryRepository);

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId("req-2");

        handlers.handleDatabaseUpdate(
                payment,
                PaymentStatus.PROCESSING,
                PaymentStatus.SUCCESS,
                "PAYMENT_COMPLETED",
                "done",
                null);

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(historyCaptor.capture());
        assertEquals("{}", historyCaptor.getValue().getMetadata());
    }
}
