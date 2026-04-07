package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AirtimePaymentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldUseReferenceAsMobileNumber() {
        AirtimePayment payment = new AirtimePayment();
        payment.setRecipientMobileNumber("255700000111");
        payment.setAmount(BigDecimal.valueOf(1000));
        payment.setMetadata("{\"networkCode\":\"VODA\",\"recipientName\":\"Alice\"}");

        Set<ConstraintViolation<AirtimePayment>> violations = validator.validate(payment);

        assertThat(payment.getReferenceNumber()).isEqualTo("255700000111");
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectInvalidMobileReference() {
        AirtimePayment payment = new AirtimePayment();
        payment.setReferenceNumber("ABC-123");
        payment.setAmount(BigDecimal.valueOf(1000));
        payment.setMetadata("{\"networkCode\":\"VODA\",\"recipientName\":\"Alice\"}");

        Set<ConstraintViolation<AirtimePayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("airtime referenceNumber must be a mobile number");
    }

    @Test
    void shouldRequireAirtimeMetadataFields() {
        AirtimePayment payment = new AirtimePayment();
        payment.setReferenceNumber("255700000222");
        payment.setAmount(BigDecimal.valueOf(1000));
        payment.setMetadata("{}");

        Set<ConstraintViolation<AirtimePayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("airtime metadata requires networkCode and recipientName");
    }
}