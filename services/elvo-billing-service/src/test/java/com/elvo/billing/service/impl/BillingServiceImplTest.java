package com.elvo.billing.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.service.orchestration.LookupFlow;
import com.elvo.billing.service.orchestration.PaymentFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private PaymentFlow paymentFlow;

    @Mock
    private LookupFlow lookupFlow;

    @Mock
    private BillPaymentRepository billPaymentRepository;

    @Mock
    private BillingEventPublisher billingEventPublisher;

    @Test
    void shouldReversePaymentUsingLockedReferenceAndPublishCompensationEvent() {
        BillingServiceImpl service = new BillingServiceImpl(paymentFlow, lookupFlow, billPaymentRepository, billingEventPublisher);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-REV-1");

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId("REQ-REV-1");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(BigDecimal.valueOf(1700));
        payment.setCurrency("TZS");

        when(billPaymentRepository.getPaymentByReferenceWithLock("REF-REV-1")).thenReturn(Optional.of(payment));

        PaymentResponseDto response = service.reversePayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(response.getMetadata()).contains("compensationTriggered");
        verify(billPaymentRepository).updatePaymentStatus(payment.getPaymentId(), PaymentStatus.REVERSED);
        verify(billingEventPublisher).publish(eq("billing.payment.reversed"), eq("REQ-REV-1"), eq(response.getMetadata()));
    }
}
