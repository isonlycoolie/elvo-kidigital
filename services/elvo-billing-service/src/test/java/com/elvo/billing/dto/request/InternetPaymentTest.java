package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class InternetPaymentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsShouldRequireAmountAndSkipLookup() {
        InternetPayment payment = new InternetPayment();
        payment.setReferenceNumber("INT-REF-001");
        payment.setAmount(BigDecimal.valueOf(20000));
        payment.setMetadata("{\"packageCode\":\"FIBER-50\",\"subscriptionPeriod\":\"MONTHLY\"}");

        Set<ConstraintViolation<InternetPayment>> violations = validator.validate(payment);

        assertThat(payment.isLookupRequired()).isFalse();
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRequireInternetMetadataFields() {
        InternetPayment payment = new InternetPayment();
        payment.setReferenceNumber("INT-REF-002");
        payment.setAmount(BigDecimal.valueOf(12000));
        payment.setMetadata("{}");

        Set<ConstraintViolation<InternetPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("internet metadata requires packageCode and subscriptionPeriod");
    }

    @Test
    void shouldRequireAmountWhenLookupIsDisabled() {
        InternetPayment payment = new InternetPayment();
        payment.setReferenceNumber("INT-REF-003");
        payment.setMetadata("{\"packageCode\":\"FIBER-50\",\"subscriptionPeriod\":\"MONTHLY\"}");

        Set<ConstraintViolation<InternetPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("amount is required when lookupRequired is false");
    }
}