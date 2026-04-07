package com.elvo.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import com.elvo.billing.dto.request.ProviderCallbackDto;
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
public class ProviderCallbackControllerTest {

    @Mock
    private BillingService billingService;

    @InjectMocks
    private ProviderCallbackController providerCallbackController;

    private ProviderCallbackDto callbackRequest;
    private PaymentResponseDto callbackResponse;

    @BeforeEach
    void setUp() {
        callbackRequest = new ProviderCallbackDto();
        callbackRequest.setReferenceNumber("REF_001");
        callbackRequest.setExternalReference("EXT_REF_001");
        callbackRequest.setStatus("SUCCESS");
        callbackRequest.setReceiptNumber("RECEIPT_123");
        callbackRequest.setMessage("payment processed by provider");
        callbackRequest.setMetadata("{\"callbackTime\":\"2026-04-08T10:00:00Z\"}");

        callbackResponse = new PaymentResponseDto();
        callbackResponse.setPaymentId(UUID.randomUUID());
        callbackResponse.setStatus(PaymentStatus.SUCCESS);
        callbackResponse.setExternalReference("EXT_REF_001");
        callbackResponse.setReceiptNumber("RECEIPT_123");
        callbackResponse.setMessage("provider callback processed");
        callbackResponse.setPaidAmount(new BigDecimal("100.00"));
        callbackResponse.setCurrency("TZS");
    }

    @Test
    void testHandleProviderCallbackSuccess() {
        when(billingService.handleProviderCallback(any(ProviderCallbackDto.class)))
                .thenReturn(callbackResponse);

        ResponseEntity<PaymentResponseDto> response = providerCallbackController.handleProviderCallback(callbackRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getBody().getExternalReference()).isEqualTo("EXT_REF_001");
        assertThat(response.getBody().getReceiptNumber()).isEqualTo("RECEIPT_123");

        verify(billingService).handleProviderCallback(any(ProviderCallbackDto.class));
    }

    @Test
    void testHandleProviderCallbackWithFailedStatus() {
        callbackRequest.setStatus("FAILED");
        callbackResponse.setStatus(PaymentStatus.FAILED);

        when(billingService.handleProviderCallback(any(ProviderCallbackDto.class)))
                .thenReturn(callbackResponse);

        ResponseEntity<PaymentResponseDto> response = providerCallbackController.handleProviderCallback(callbackRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.FAILED);

        verify(billingService).handleProviderCallback(any(ProviderCallbackDto.class));
    }
}
