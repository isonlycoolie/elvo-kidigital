package com.elvo.identity.exception;

import java.util.UUID;

public class PendingVerificationException extends RuntimeException {

    private final UUID userId;

    public PendingVerificationException(String message, UUID userId) {
        super(message);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
