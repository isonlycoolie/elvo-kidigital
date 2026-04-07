package com.elvo.billing.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.mapper.GenericRequestMapper;
import com.elvo.billing.mapper.GenericResponseMapper;
import org.junit.jupiter.api.Test;

class SelcomAdapterTest {

    private final SelcomAdapter adapter = new SelcomAdapter(
            new GenericRequestMapper(),
            new GenericResponseMapper(),
            "https://api.sandbox.selcom.example",
            "test-key",
            "test-secret");

        private final SelcomAdapter adapterWithoutCredentials = new SelcomAdapter(
            new GenericRequestMapper(),
            new GenericResponseMapper(),
            "https://api.sandbox.selcom.example",
            "",
            "");

    @Test
    void shouldProduceDeterministicLookupResponse() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("SEL-LOOKUP-001");
        request.setCustomerName("Lookup Customer");
        request.setAmount(BigDecimal.valueOf(1200));
        request.setMetadata("{\"source\":\"test\"}");

        LookupResponseDto response = adapter.lookup(request);

        assertThat(response.getLookupStatus()).isEqualTo(LookupStatus.SUCCESS);
        assertThat(response.getCustomerName()).isEqualTo("Lookup Customer");
        assertThat(response.getReferenceNumber()).isEqualTo("SEL-LOOKUP-001");
        assertThat(response.getAmount()).isEqualTo(BigDecimal.valueOf(1200));
        assertThat(response.getCurrency()).isEqualTo("TZS");
        assertThat(response.getRawProviderReference()).isEqualTo("SELCOM-SEL-LOOKUP-001");
    }

    @Test
    void shouldProduceDeterministicPaymentResponse() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("SEL-PAY-001");
        request.setAmount(BigDecimal.valueOf(3100));
        request.setMetadata("{\"source\":\"test\"}");

        PaymentResponseDto response = adapter.pay(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getExternalReference()).isEqualTo("SELCOM-SEL-PAY-001");
        assertThat(response.getReceiptNumber()).isEqualTo("SEL-" + "SEL-PAY-001".hashCode());
        assertThat(response.getPaidAmount()).isEqualTo(BigDecimal.valueOf(3100));
        assertThat(response.getCurrency()).isEqualTo("TZS");
        assertThat(response.getMetadata()).contains("\"provider\":\"selcom\"");
        assertThat(response.getMetadata()).contains("\"authConfigured\":true");
    }

    @Test
    void shouldUseFallbackValuesWhenOptionalFieldsAreMissing() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("SEL-LOOKUP-002");

        LookupResponseDto response = adapter.lookup(request);

        assertThat(response.getCustomerName()).isEqualTo("Selcom Customer");
        assertThat(response.getAmount()).isNotNull();
        assertThat(response.getCurrency()).isEqualTo("TZS");
    }

    @Test
    void shouldMarkAuthAsNotConfiguredWhenCredentialsAreMissing() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("SEL-PAY-002");
        request.setAmount(BigDecimal.valueOf(99));

        PaymentResponseDto response = adapterWithoutCredentials.pay(request);

        assertThat(response.getMetadata()).contains("\"authConfigured\":false");
    }
}