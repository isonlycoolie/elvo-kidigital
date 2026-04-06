package com.elvo.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmailRegistrationRequest {

    @Email
    @NotBlank
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @Size(max = 128)
    private String displayName;

    private boolean enableMfa;

    private String sourceIp;

    private String sourceUserAgent;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnableMfa() {
        return enableMfa;
    }

    public void setEnableMfa(boolean enableMfa) {
        this.enableMfa = enableMfa;
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
