package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class UtilityPaymentRequestDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsShouldMatchBaseContract() {
        UtilityPaymentRequestDto dto = new UtilityPaymentRequestDto();

        assertThat(dto.getMetadata()).isEqualTo("{}");
        assertThat(dto.isLookupRequired()).isFalse();
    }

    @Test
    void validationShouldRequireReferenceNumber() {
        UtilityPaymentRequestDto dto = new UtilityPaymentRequestDto();
        dto.setAmount(new BigDecimal("100.00"));

        Set<ConstraintViolation<UtilityPaymentRequestDto>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("referenceNumber is required");
    }

    @Test
    void validationShouldRequireAmountWhenLookupIsNotRequired() {
        UtilityPaymentRequestDto dto = new UtilityPaymentRequestDto();
        dto.setReferenceNumber("REF-VALIDATION-001");
        dto.setLookupRequired(false);

        Set<ConstraintViolation<UtilityPaymentRequestDto>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("amount is required when lookupRequired is false");
    }

    @Test
    void validationShouldAllowMissingAmountWhenLookupIsRequired() {
        UtilityPaymentRequestDto dto = new UtilityPaymentRequestDto();
        dto.setReferenceNumber("REF-VALIDATION-002");
        dto.setLookupRequired(true);

        Set<ConstraintViolation<UtilityPaymentRequestDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
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