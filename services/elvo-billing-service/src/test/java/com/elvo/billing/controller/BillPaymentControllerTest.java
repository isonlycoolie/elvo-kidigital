package com.elvo.billing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.service.BillingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BillPaymentController.class)
public class BillPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BillingService billingService;

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
    void testCreatePaymentSuccess() throws Exception {
        when(billingService.executePayment(any(UtilityPaymentRequestDto.class)))
                .thenReturn(paymentResponse);

        mockMvc.perform(post("/api/v1/bill-payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest))
                .header("X-Correlation-ID", "test-correlation-id-123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("payment created"))
                .andExpect(jsonPath("$.paidAmount").value(100.00));

        verify(billingService).executePayment(any(UtilityPaymentRequestDto.class));
    }

    @Test
    void testCreatePaymentWithoutCorrelationId() throws Exception {
        when(billingService.executePayment(any(UtilityPaymentRequestDto.class)))
                .thenReturn(paymentResponse);

        mockMvc.perform(post("/api/v1/bill-payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(billingService).executePayment(any(UtilityPaymentRequestDto.class));
    }

    @Test
    void testCreatePaymentValidationFailure() throws Exception {
        paymentRequest.setReferenceNumber(null);

        mockMvc.perform(post("/api/v1/bill-payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isBadRequest());
    }
}
