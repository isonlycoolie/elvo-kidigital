package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class WaterPaymentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsShouldRequireLookupAndAllowAmountFromLookup() {
        WaterPayment payment = new WaterPayment();
        payment.setReferenceNumber("WATER-REF-001");
        payment.setMetadata("{\"institution\":\"DAWASA\",\"billingPeriod\":\"2026-03\"}");

        Set<ConstraintViolation<WaterPayment>> violations = validator.validate(payment);

        assertThat(payment.isLookupRequired()).isTrue();
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRequireWaterMetadataFields() {
        WaterPayment payment = new WaterPayment();
        payment.setReferenceNumber("WATER-REF-002");
        payment.setMetadata("{}");

        Set<ConstraintViolation<WaterPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("water metadata requires institution and billingPeriod");
    }

    @Test
    void shouldPopulateWaterMetadataThroughSetters() {
        WaterPayment payment = new WaterPayment();
        payment.setReferenceNumber("WATER-REF-003");
        payment.setInstitution("DAWASA");
        payment.setBillingPeriod("2026-03");

        assertThat(payment.getMetadata()).contains("\"institution\":\"DAWASA\"");
        assertThat(payment.getMetadata()).contains("\"billingPeriod\":\"2026-03\"");
    }
}