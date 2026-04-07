package com.elvo.billing.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

class GenericResponseMapperTest {

    private final GenericResponseMapper mapper = new GenericResponseMapper();

    @Test
    void shouldMapProviderPaymentResponseToDto() {
        UUID paymentId = UUID.randomUUID();

        PaymentResponseDto response = mapper.toPaymentResponse(Map.of(
                "paymentId", paymentId.toString(),
                "externalReference", "EXT-200",
                "status", "success",
                "message", "Payment completed",
                "receiptNumber", "RCP-200",
                "paidAmount", "1250.50",
                "currency", "TZS",
                "completedAt", Instant.parse("2026-04-07T10:15:30Z").toString(),
                "metadata", Map.of("provider", "selcom", "attempt", 1)
        ));

        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getExternalReference()).isEqualTo("EXT-200");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getMessage()).isEqualTo("Payment completed");
        assertThat(response.getReceiptNumber()).isEqualTo("RCP-200");
        assertThat(response.getPaidAmount()).isEqualTo(new BigDecimal("1250.50"));
        assertThat(response.getCurrency()).isEqualTo("TZS");
        assertThat(response.getCompletedAt()).isEqualTo(Instant.parse("2026-04-07T10:15:30Z"));
        assertThat(response.getMetadata()).contains("\"provider\":\"selcom\"");
    }

    @Test
    void shouldMapProviderLookupResponseToDto() {
        LookupResponseDto response = mapper.toLookupResponse(Map.of(
                "lookupStatus", "success",
                "customerName", "Test Customer",
                "referenceNumber", "REF-LOOKUP-200",
                "amount", 45.75,
                "currency", "TZS",
                "description", "Utility lookup successful",
                "billItems", List.of(Map.of("label", "Service", "amount", 25.0)),
                "rawProviderReference", "RAW-LOOKUP-200"
        ));

        assertThat(response.getLookupStatus()).isEqualTo(LookupStatus.SUCCESS);
        assertThat(response.getCustomerName()).isEqualTo("Test Customer");
        assertThat(response.getReferenceNumber()).isEqualTo("REF-LOOKUP-200");
        assertThat(response.getAmount()).isEqualTo(new BigDecimal("45.75"));
        assertThat(response.getCurrency()).isEqualTo("TZS");
        assertThat(response.getDescription()).isEqualTo("Utility lookup successful");
        assertThat(response.getBillItems()).contains("label");
        assertThat(response.getRawProviderReference()).isEqualTo("RAW-LOOKUP-200");
    }

    @Test
    void shouldReturnNullsForMissingOptionalFields() {
        PaymentResponseDto paymentResponse = mapper.toPaymentResponse(Map.of("status", "failed"));
        LookupResponseDto lookupResponse = mapper.toLookupResponse(Map.of("lookupStatus", "not_found"));

        assertThat(paymentResponse.getPaymentId()).isNull();
        assertThat(paymentResponse.getExternalReference()).isNull();
        assertThat(paymentResponse.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(lookupResponse.getLookupStatus()).isEqualTo(LookupStatus.NOT_FOUND);
        assertThat(lookupResponse.getCustomerName()).isNull();
    }
}