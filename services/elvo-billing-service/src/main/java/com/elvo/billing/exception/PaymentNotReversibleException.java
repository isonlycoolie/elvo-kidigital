package com.elvo.billing.exception;

public class PaymentNotReversibleException extends RuntimeException {

    public PaymentNotReversibleException(String message) {
        super(message);
    }

    public PaymentNotReversibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
