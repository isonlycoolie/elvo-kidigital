package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ElectricityPaymentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsShouldRequireAmountAndSkipLookup() {
        ElectricityPayment payment = new ElectricityPayment();
        payment.setReferenceNumber("ELEC-REF-001");
        payment.setAmount(BigDecimal.valueOf(3000));
        payment.setMetadata("{\"meterType\":\"PREPAID\",\"providerRegion\":\"DSM\"}");

        Set<ConstraintViolation<ElectricityPayment>> violations = validator.validate(payment);

        assertThat(payment.isLookupRequired()).isFalse();
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRequireAmountForElectricityPayment() {
        ElectricityPayment payment = new ElectricityPayment();
        payment.setReferenceNumber("ELEC-REF-002");
        payment.setMetadata("{\"meterType\":\"PREPAID\",\"providerRegion\":\"DSM\"}");

        Set<ConstraintViolation<ElectricityPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("amount is required when lookupRequired is false");
    }

    @Test
    void shouldRequireElectricityMetadataFields() {
        ElectricityPayment payment = new ElectricityPayment();
        payment.setReferenceNumber("ELEC-REF-003");
        payment.setAmount(BigDecimal.valueOf(2000));
        payment.setMetadata("{}");

        Set<ConstraintViolation<ElectricityPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("electricity metadata requires meterType and providerRegion");
    }
}