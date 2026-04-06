package com.elvo.wallet.exception;

public class IdempotencyReplayDetectedException extends RuntimeException {

    public IdempotencyReplayDetectedException(String message) {
        super(message);
    }
}