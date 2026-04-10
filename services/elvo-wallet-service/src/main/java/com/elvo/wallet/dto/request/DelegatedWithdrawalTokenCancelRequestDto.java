package com.elvo.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DelegatedWithdrawalTokenCancelRequestDto {

    @NotBlank(message = "Reason is required")
    @Size(max = 256, message = "Reason must be 256 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9 ._:@+-]+$", message = "Reason contains unsupported characters")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
