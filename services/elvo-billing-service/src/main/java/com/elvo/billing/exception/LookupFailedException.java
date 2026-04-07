package com.elvo.billing.exception;

public class LookupFailedException extends RuntimeException {

    public LookupFailedException(String message) {
        super(message);
    }

    public LookupFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}