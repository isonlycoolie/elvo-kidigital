package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class HospitalPaymentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsShouldRequireLookupAndAllowAmountFromLookup() {
        HospitalPayment payment = new HospitalPayment();
        payment.setReferenceNumber("HOSP-REF-001");
        payment.setMetadata("{\"hospitalCode\":\"HOSP-DSM\",\"billingPeriod\":\"2026-03\"}");

        Set<ConstraintViolation<HospitalPayment>> violations = validator.validate(payment);

        assertThat(payment.isLookupRequired()).isTrue();
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRequireHospitalMetadataFields() {
        HospitalPayment payment = new HospitalPayment();
        payment.setReferenceNumber("HOSP-REF-002");
        payment.setMetadata("{}");

        Set<ConstraintViolation<HospitalPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("hospital metadata requires hospitalCode and billingPeriod");
    }

    @Test
    void shouldPopulateHospitalMetadataThroughSetters() {
        HospitalPayment payment = new HospitalPayment();
        payment.setReferenceNumber("HOSP-REF-003");
        payment.setHospitalCode("HOSP-DSM");
        payment.setBillingPeriod("2026-03");

        assertThat(payment.getMetadata()).contains("\"hospitalCode\":\"HOSP-DSM\"");
        assertThat(payment.getMetadata()).contains("\"billingPeriod\":\"2026-03\"");
    }
}