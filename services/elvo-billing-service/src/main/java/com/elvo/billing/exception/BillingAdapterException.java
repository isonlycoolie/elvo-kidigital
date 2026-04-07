package com.elvo.billing.exception;

public class BillingAdapterException extends RuntimeException {

    public BillingAdapterException(String message) {
        super(message);
    }

    public BillingAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
