package com.elvo.billing.service.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.elvo.billing.audit.PaymentAuditLogger;
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
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.service.impl.IdempotencyEnforcer;
import com.elvo.billing.validator.UtilityPaymentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentFlowTest {

    @Mock
    private UtilityPaymentValidator validator;

    @Mock
    private ProviderResolver providerResolver;

    @Mock
    private BillPaymentRepository billPaymentRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private BillingAdapter adapter;

    @Mock
    private BillingEventPublisher billingEventPublisher;

    @Mock
    private IdempotencyEnforcer idempotencyEnforcer;

    @Mock
    private PaymentAuditLogger paymentAuditLogger;

    @Test
    void shouldExecutePaymentAndPersistPaymentHistory() {
        PaymentFlow flow = new PaymentFlow(
                validator,
                providerResolver,
                billPaymentRepository,
                paymentHistoryRepository,
                billingEventPublisher,
                idempotencyEnforcer,
                paymentAuditLogger);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("PAY-001");
        request.setAmount(BigDecimal.valueOf(1200));
        request.setMetadata("{}");

        PaymentResponseDto adapterResponse = new PaymentResponseDto();
        adapterResponse.setPaymentId(UUID.randomUUID());
        adapterResponse.setStatus(PaymentStatus.SUCCESS);
        adapterResponse.setMessage("ok");
        adapterResponse.setExternalReference("EXT-1");
        adapterResponse.setReceiptNumber("REC-1");
        adapterResponse.setPaidAmount(BigDecimal.valueOf(1200));
        adapterResponse.setCurrency("TZS");
        adapterResponse.setCompletedAt(Instant.now());
        adapterResponse.setMetadata("{}");

        when(providerResolver.resolve("LUKU")).thenReturn(adapter);
        when(adapter.pay(request)).thenReturn(adapterResponse);

        PaymentResponseDto result = flow.execute(
                request,
                BillCategory.ELECTRICITY,
                "LUKU",
                "REQ-1",
                "CORR-1",
                "IDEMP-1",
                UUID.randomUUID(),
                UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(validator).validateForPayment(eq(request), eq(BillCategory.ELECTRICITY));
        verify(idempotencyEnforcer).assertNotProcessed(eq("IDEMP-1"), eq("PAYMENT_EXECUTE"), eq("LUKU|PAY-001|1200"));
        verify(providerResolver).resolve("LUKU");
        verify(adapter).pay(request);

        ArgumentCaptor<BillPayment> paymentCaptor = ArgumentCaptor.forClass(BillPayment.class);
        verify(billPaymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getServiceCode()).isEqualTo("LUKU");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEventType()).isEqualTo("PAYMENT_EXECUTED");
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo("SUCCESS");
        verify(paymentAuditLogger).logUpdate(paymentCaptor.getValue(), "PAYMENT_EXECUTED", "PENDING", "SUCCESS");
        verify(billingEventPublisher).publish(eq("billing.payment.completed"), eq("REQ-1"), eq("{}"));
        verify(idempotencyEnforcer).markProcessed(eq("IDEMP-1"), eq("PAYMENT_EXECUTE"), eq("LUKU|PAY-001|1200"), eq("{}"));
    }
}
