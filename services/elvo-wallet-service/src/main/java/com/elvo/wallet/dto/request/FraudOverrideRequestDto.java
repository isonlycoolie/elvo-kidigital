package com.elvo.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;

public class FraudOverrideRequestDto {

    @NotBlank(message = "Fraud override decision is required")
    private String decision;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}
