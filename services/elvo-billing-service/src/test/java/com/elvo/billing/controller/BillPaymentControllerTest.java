package com.elvo.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.service.BillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
public class BillPaymentControllerTest {

    @Mock
    private BillingService billingService;

    @InjectMocks
    private BillPaymentController billPaymentController;

    private UtilityPaymentRequestDto paymentRequest;
    private PaymentResponseDto paymentResponse;

    @BeforeEach
    void setUp() {
        paymentRequest = new UtilityPaymentRequestDto();
        paymentRequest.setReferenceNumber("REF_001");
        paymentRequest.setAmount(new BigDecimal("100.00"));
        paymentRequest.setCustomerPhone("+255700000001");
        paymentRequest.setCustomerName("John Doe");
        paymentRequest.setMetadata("{\"category\":\"ELECTRICITY\"}");

        paymentResponse = new PaymentResponseDto();
        paymentResponse.setPaymentId(UUID.randomUUID());
        paymentResponse.setStatus(PaymentStatus.PENDING);
        paymentResponse.setMessage("payment created");
        paymentResponse.setPaidAmount(new BigDecimal("100.00"));
        paymentResponse.setCurrency("TZS");
    }

    @Test
    void testCreatePaymentSuccess() {
        when(billingService.executePayment(any(UtilityPaymentRequestDto.class)))
                .thenReturn(paymentResponse);

        ResponseEntity<PaymentResponseDto> response = billPaymentController.createPayment(paymentRequest, "test-correlation-id-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getBody().getMessage()).isEqualTo("payment created");
        assertThat(response.getBody().getPaidAmount()).isEqualTo(new BigDecimal("100.00"));

        verify(billingService).executePayment(any(UtilityPaymentRequestDto.class));
    }

    @Test
    void testCreatePaymentWithoutCorrelationId() {
        when(billingService.executePayment(any(UtilityPaymentRequestDto.class)))
                .thenReturn(paymentResponse);

        ResponseEntity<PaymentResponseDto> response = billPaymentController.createPayment(paymentRequest, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(billingService).executePayment(any(UtilityPaymentRequestDto.class));
    }
}
