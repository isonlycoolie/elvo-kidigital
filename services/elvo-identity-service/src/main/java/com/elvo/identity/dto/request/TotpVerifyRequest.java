package com.elvo.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class TotpVerifyRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be 6 digits")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}