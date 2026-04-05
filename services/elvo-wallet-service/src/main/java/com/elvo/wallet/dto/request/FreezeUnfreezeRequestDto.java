package com.elvo.wallet.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class FreezeUnfreezeRequestDto {

    @Size(max = 256, message = "Reason must be 256 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9 .,_:;\\-()]*$", message = "Reason may only contain letters, numbers, spaces, and basic punctuation")
    private String reason;

    public FreezeUnfreezeRequestDto() {
    }

    public FreezeUnfreezeRequestDto(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
