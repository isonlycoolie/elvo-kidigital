package com.elvo.billing.exception;

/**
 * Exception thrown when callback verification fails.
 * Can be signature validation, timestamp, source, or replay check.
 */
public class CallbackVerificationFailedException extends RuntimeException {
    
    public CallbackVerificationFailedException(String message) {
        super(message);
    }

    public CallbackVerificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
