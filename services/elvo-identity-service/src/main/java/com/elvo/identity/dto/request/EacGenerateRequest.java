package com.elvo.identity.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.elvo.identity.validation.ActionName;

public class EacGenerateRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private UUID sessionId;

    @NotNull
    private UUID deviceId;

    @NotNull
    @ActionName
    private String action;

    private String sourceIp;

    private String sourceUserAgent;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getSourceUserAgent() {
        return sourceUserAgent;
    }

    public void setSourceUserAgent(String sourceUserAgent) {
        this.sourceUserAgent = sourceUserAgent;
    }
}
