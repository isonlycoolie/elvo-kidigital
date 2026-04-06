package com.elvo.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;

public class AmlCaseResolutionRequestDto {

    private boolean suspiciousActivityConfirmed;

    @NotBlank(message = "Resolution notes are required")
    private String resolutionNotes;

    public boolean isSuspiciousActivityConfirmed() {
        return suspiciousActivityConfirmed;
    }

    public void setSuspiciousActivityConfirmed(boolean suspiciousActivityConfirmed) {
        this.suspiciousActivityConfirmed = suspiciousActivityConfirmed;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }
}
