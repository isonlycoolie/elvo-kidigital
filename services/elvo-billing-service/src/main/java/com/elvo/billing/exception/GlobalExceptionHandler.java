package com.elvo.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.elvo.billing.monitoring.SentryExceptionMapper;
import com.elvo.billing.security.SensitiveDataMasker;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SentryExceptionMapper sentryExceptionMapper;

    public GlobalExceptionHandler(SentryExceptionMapper sentryExceptionMapper) {
        this.sentryExceptionMapper = sentryExceptionMapper;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        sentryExceptionMapper.capture(ex, "VALIDATION_ERROR");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", "Request validation failed"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        sentryExceptionMapper.capture(ex, "CONSTRAINT_VIOLATION");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("CONSTRAINT_VIOLATION", "Constraint validation failed"));
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentValidation(PaymentValidationException ex) {
        sentryExceptionMapper.capture(ex, "PAYMENT_VALIDATION_ERROR");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("PAYMENT_VALIDATION_ERROR", SensitiveDataMasker.maskText(ex.getMessage())));
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicatePayment(DuplicatePaymentException ex) {
        sentryExceptionMapper.capture(ex, "DUPLICATE_PAYMENT");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DUPLICATE_PAYMENT", SensitiveDataMasker.maskText(ex.getMessage())));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RateLimitExceededException ex) {
        sentryExceptionMapper.capture(ex, "RATE_LIMIT_EXCEEDED");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("RATE_LIMIT_EXCEEDED", "Too many requests"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        sentryExceptionMapper.capture(ex, "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Unexpected server error"));
    }
}
