package com.elvo.billing.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.elvo.billing.monitoring.SentryExceptionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private SentryExceptionMapper sentryExceptionMapper;

    @Test
    void shouldMapPaymentValidationExceptionToBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(sentryExceptionMapper);

        PaymentValidationException ex = new PaymentValidationException("invalid payment");
        ResponseEntity<ApiResponse<Void>> response = handler.handlePaymentValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PAYMENT_VALIDATION_ERROR");
        verify(sentryExceptionMapper).capture(ex, "PAYMENT_VALIDATION_ERROR");
    }

    @Test
    void shouldMapDuplicatePaymentExceptionToConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(sentryExceptionMapper);

        DuplicatePaymentException ex = new DuplicatePaymentException("duplicate detected");
        ResponseEntity<ApiResponse<Void>> response = handler.handleDuplicatePayment(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_PAYMENT");
        verify(sentryExceptionMapper).capture(ex, "DUPLICATE_PAYMENT");
    }
}
