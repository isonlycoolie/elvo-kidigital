package com.elvo.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.service.BillingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BillingService billingService;

    @Test
    @WithMockUser(username = "api-user", roles = "USER")
    void createPaymentShouldReturnCreatedForValidPayload() throws Exception {
        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(UUID.randomUUID());
        response.setStatus(PaymentStatus.SUCCESS);
        response.setPaidAmount(new BigDecimal("100.00"));
        response.setCurrency("TZS");
        response.setMessage("accepted");

        when(billingService.executePayment(any())).thenReturn(response);

        String payload = """
                {
                  "referenceNumber": "API-PAY-001",
                  "amount": 100.00,
                  "metadata": "{\\"meterType\\":\\"PREPAID\\"}"
                }
                """;

        mockMvc.perform(post("/api/v1/bill-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @WithMockUser(username = "api-user", roles = "USER")
    void createPaymentShouldReturnBadRequestForInvalidPayload() throws Exception {
        String payload = """
                {
                  "amount": -10,
                  "metadata": "{}"
                }
                """;

        mockMvc.perform(post("/api/v1/bill-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "api-user", roles = "USER")
    void providerCallbackShouldReturnBadRequestForMissingMandatoryFields() throws Exception {
        String payload = """
                {
                  "referenceNumber": "API-CB-001",
                  "externalReference": "EXT-1"
                }
                """;

        mockMvc.perform(post("/api/v1/internal/bill-payments/provider-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void securedEndpointShouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/bill-payments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
