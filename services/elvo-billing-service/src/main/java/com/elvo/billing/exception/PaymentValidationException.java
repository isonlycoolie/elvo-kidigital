package com.elvo.billing.exception;

import java.util.Map;

public class PaymentValidationException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    public PaymentValidationException(String message) {
        super(message);
        this.fieldErrors = Map.of();
    }

    public PaymentValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public PaymentValidationException(String message, Throwable cause) {
        super(message, cause);
        this.fieldErrors = Map.of();
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}