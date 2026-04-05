package com.elvo.identity.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class EspVerifyRequest {

    @NotNull
    private UUID userId;

    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$")
    private String espCode;

    private String sourceIp;

    private String sourceUserAgent;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEspCode() {
        return espCode;
    }

    public void setEspCode(String espCode) {
        this.espCode = espCode;
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
