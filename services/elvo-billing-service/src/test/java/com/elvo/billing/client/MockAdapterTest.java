package com.elvo.billing.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

class MockAdapterTest {

    private final MockAdapter adapter = new MockAdapter();

    @Test
    void shouldReturnDeterministicLookupResponse() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("MOCK-LOOKUP-001");
        request.setLookupRequired(true);

        LookupResponseDto response = adapter.lookup(request);

        assertThat(response.getLookupStatus()).isEqualTo(LookupStatus.SUCCESS);
        assertThat(response.getCustomerName()).isEqualTo("Mock Customer");
        assertThat(response.getReferenceNumber()).isEqualTo("MOCK-LOOKUP-001");
        assertThat(response.getAmount()).isEqualTo(BigDecimal.valueOf(Math.abs("MOCK-LOOKUP-001".hashCode() % 100_000) / 100.0));
        assertThat(response.getCurrency()).isEqualTo("TZS");
        assertThat(response.getDescription()).isEqualTo("Mock lookup response");
        assertThat(response.getRawProviderReference()).isEqualTo("MOCK-MOCK-LOOKUP-001");
    }

    @Test
    void shouldReturnDeterministicPaymentResponse() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("MOCK-PAY-001");
        request.setAmount(BigDecimal.valueOf(4500));
        request.setCustomerName("Payment Customer");

        PaymentResponseDto response = adapter.pay(request);

        UUID expectedPaymentId = UUID.nameUUIDFromBytes(("mock|MOCK-PAY-001").getBytes(StandardCharsets.UTF_8));

        assertThat(response.getPaymentId()).isEqualTo(expectedPaymentId);
        assertThat(response.getExternalReference()).isEqualTo("MOCK-MOCK-PAY-001");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getMessage()).isEqualTo("Mock payment completed");
        assertThat(response.getReceiptNumber()).isEqualTo("MOCK-REC-" + Math.abs("MOCK-PAY-001".hashCode()));
        assertThat(response.getPaidAmount()).isEqualTo(BigDecimal.valueOf(4500));
        assertThat(response.getCurrency()).isEqualTo("TZS");
        assertThat(response.getCompletedAt()).isEqualTo(Instant.parse("2026-04-07T00:00:00Z"));
        assertThat(response.getMetadata()).isEqualTo("{\"provider\":\"mock\",\"mode\":\"deterministic\",\"referenceNumber\":\"MOCK-PAY-001\",\"lookupRequired\":false}");
    }
}