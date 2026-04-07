package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AirlinePaymentTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsShouldRequireLookupAndAllowAmountFromLookup() {
        AirlinePayment payment = new AirlinePayment();
        payment.setReferenceNumber("AIR-REF-001");
        payment.setMetadata("{\"bookingCode\":\"BKG-100\",\"passengerName\":\"John Doe\",\"travelDate\":\"2026-06-01\"}");

        Set<ConstraintViolation<AirlinePayment>> violations = validator.validate(payment);

        assertThat(payment.isLookupRequired()).isTrue();
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRequireAirlineMetadataFields() {
        AirlinePayment payment = new AirlinePayment();
        payment.setReferenceNumber("AIR-REF-002");
        payment.setMetadata("{}");

        Set<ConstraintViolation<AirlinePayment>> violations = validator.validate(payment);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("airline metadata requires bookingCode, passengerName, and travelDate");
    }

    @Test
    void shouldPopulateAirlineMetadataThroughSetters() {
        AirlinePayment payment = new AirlinePayment();
        payment.setReferenceNumber("AIR-REF-003");
        payment.setBookingCode("BKG-100");
        payment.setPassengerName("John Doe");
        payment.setTravelDate("2026-06-01");

        assertThat(payment.getMetadata()).contains("\"bookingCode\":\"BKG-100\"");
        assertThat(payment.getMetadata()).contains("\"passengerName\":\"John Doe\"");
        assertThat(payment.getMetadata()).contains("\"travelDate\":\"2026-06-01\"");
    }
}