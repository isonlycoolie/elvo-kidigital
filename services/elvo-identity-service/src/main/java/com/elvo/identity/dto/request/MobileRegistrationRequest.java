package com.elvo.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MobileRegistrationRequest {

    @NotBlank
    @Pattern(regexp = "^[0-9+]{7,20}$")
    private String phone;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @Size(max = 128)
    private String displayName;

    private boolean enableMfa;

    private String sourceIp;

    private String sourceUserAgent;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
