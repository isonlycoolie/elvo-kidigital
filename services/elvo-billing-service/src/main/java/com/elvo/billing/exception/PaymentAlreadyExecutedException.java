package com.elvo.billing.exception;

public class PaymentAlreadyExecutedException extends RuntimeException {

    public PaymentAlreadyExecutedException(String message) {
        super(message);
    }

    public PaymentAlreadyExecutedException(String message, Throwable cause) {
        super(message, cause);
    }
}
