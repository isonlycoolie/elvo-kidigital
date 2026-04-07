package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class TvSubscriptionPaymentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsShouldRequireAmountAndSkipLookup() {
        TvSubscriptionPayment payment = new TvSubscriptionPayment();
        payment.setReferenceNumber("TV-REF-001");
        payment.setAmount(BigDecimal.valueOf(15000));
        payment.setMetadata("{\"packageCode\":\"DSTV-PREMIUM\",\"packageName\":\"Premium\",\"subscriptionPeriod\":\"MONTHLY\"}");

        Set<ConstraintViolation<TvSubscriptionPayment>> violations = validator.validate(payment);

        assertThat(payment.isLookupRequired()).isFalse();
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRequireTvSubscriptionMetadataFields() {
        TvSubscriptionPayment payment = new TvSubscriptionPayment();
        payment.setReferenceNumber("TV-REF-002");
        payment.setAmount(BigDecimal.valueOf(12000));
        payment.setMetadata("{}");

        Set<ConstraintViolation<TvSubscriptionPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("tv subscription metadata requires packageCode, packageName, and subscriptionPeriod");
    }

    @Test
    void shouldRequireAmountWhenLookupIsDisabled() {
        TvSubscriptionPayment payment = new TvSubscriptionPayment();
        payment.setReferenceNumber("TV-REF-003");
        payment.setMetadata("{\"packageCode\":\"DSTV-PREMIUM\",\"packageName\":\"Premium\",\"subscriptionPeriod\":\"MONTHLY\"}");

        Set<ConstraintViolation<TvSubscriptionPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("amount is required when lookupRequired is false");
    }
}