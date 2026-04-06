package com.elvo.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;

public class MakerCheckerDecisionRequestDto {

    private boolean approved;

    @NotBlank(message = "Decision reason is required")
    private String reason;

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
