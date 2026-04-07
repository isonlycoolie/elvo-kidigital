package com.elvo.billing.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.elvo.billing.entity.enums.LookupStatus;

class LookupResponseDtoTest {

    @Test
    void settersShouldPopulateFields() {
        LookupResponseDto dto = new LookupResponseDto();

        dto.setLookupStatus(LookupStatus.SUCCESS);
        dto.setCustomerName("Lookup User");
        dto.setReferenceNumber("REF-LOOKUP-001");
        dto.setAmount(new BigDecimal("45.00"));
        dto.setCurrency("TZS");
        dto.setDescription("Utility lookup successful");
        dto.setBillItems("[]");
        dto.setRawProviderReference("RAW-LOOKUP-001");

        assertThat(dto.getLookupStatus()).isEqualTo(LookupStatus.SUCCESS);
        assertThat(dto.getCustomerName()).isEqualTo("Lookup User");
        assertThat(dto.getReferenceNumber()).isEqualTo("REF-LOOKUP-001");
        assertThat(dto.getAmount()).isEqualTo(new BigDecimal("45.00"));
        assertThat(dto.getCurrency()).isEqualTo("TZS");
        assertThat(dto.getDescription()).isEqualTo("Utility lookup successful");
        assertThat(dto.getBillItems()).isEqualTo("[]");
        assertThat(dto.getRawProviderReference()).isEqualTo("RAW-LOOKUP-001");
    }
}