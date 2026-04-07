package com.elvo.billing.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionsTest {

    @Test
    void paymentValidationExceptionShouldCarryMessage() {
        PaymentValidationException exception = new PaymentValidationException("referenceNumber is required");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("referenceNumber is required");
    }

    @Test
    void lookupFailedExceptionShouldCarryMessage() {
        LookupFailedException exception = new LookupFailedException("lookup failed for serviceCode");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("lookup failed for serviceCode");
    }

    @Test
    void duplicatePaymentExceptionShouldCarryMessage() {
        DuplicatePaymentException exception = new DuplicatePaymentException("duplicate idempotencyKey detected");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("duplicate idempotencyKey detected");
    }
}