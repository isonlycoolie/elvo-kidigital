package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class GovernmentPaymentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsShouldRequireLookupAndAmountFromLookupFlow() {
        GovernmentPayment payment = new GovernmentPayment();
        payment.setReferenceNumber("GOV-REF-001");
        payment.setMetadata("{\"institution\":\"TRA\",\"billDescription\":\"Tax\",\"billType\":\"INCOME_TAX\"}");

        Set<ConstraintViolation<GovernmentPayment>> violations = validator.validate(payment);

        assertThat(payment.isLookupRequired()).isTrue();
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRequireGovernmentMetadataFields() {
        GovernmentPayment payment = new GovernmentPayment();
        payment.setReferenceNumber("GOV-REF-002");
        payment.setMetadata("{}");

        Set<ConstraintViolation<GovernmentPayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("government metadata requires institution, billDescription, and billType");
    }

    @Test
    void shouldPopulateMetadataThroughGovernmentSetters() {
        GovernmentPayment payment = new GovernmentPayment();
        payment.setReferenceNumber("GOV-REF-003");
        payment.setInstitution("TRA");
        payment.setBillDescription("Tax");
        payment.setBillType("INCOME_TAX");

        assertThat(payment.getMetadata()).contains("\"institution\":\"TRA\"");
        assertThat(payment.getMetadata()).contains("\"billDescription\":\"Tax\"");
        assertThat(payment.getMetadata()).contains("\"billType\":\"INCOME_TAX\"");
    }
}