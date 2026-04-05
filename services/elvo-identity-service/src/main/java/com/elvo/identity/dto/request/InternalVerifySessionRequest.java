package com.elvo.identity.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public class InternalVerifySessionRequest {

    @NotNull
    private UUID sessionId;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }
}
