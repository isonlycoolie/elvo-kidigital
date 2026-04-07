package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class UtilityPaymentRequestDtoTest {

    @Test
    void defaultsShouldMatchBaseContract() {
        UtilityPaymentRequestDto dto = new UtilityPaymentRequestDto();

        assertThat(dto.getMetadata()).isEqualTo("{}");
        assertThat(dto.isLookupRequired()).isFalse();
    }

    @Test
    void settersShouldPopulateFields() {
        UtilityPaymentRequestDto dto = new UtilityPaymentRequestDto();

        dto.setReferenceNumber("REF-UTILITY-001");
        dto.setAmount(new BigDecimal("100.00"));
        dto.setCustomerPhone("255700000100");
        dto.setCustomerName("Test User");
        dto.setMetadata("{\"source\":\"unit-test\"}");
        dto.setLookupRequired(true);

        assertThat(dto.getReferenceNumber()).isEqualTo("REF-UTILITY-001");
        assertThat(dto.getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(dto.getCustomerPhone()).isEqualTo("255700000100");
        assertThat(dto.getCustomerName()).isEqualTo("Test User");
        assertThat(dto.getMetadata()).isEqualTo("{\"source\":\"unit-test\"}");
        assertThat(dto.isLookupRequired()).isTrue();
    }
}