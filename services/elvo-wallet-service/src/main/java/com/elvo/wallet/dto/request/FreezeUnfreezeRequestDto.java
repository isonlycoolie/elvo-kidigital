package com.elvo.wallet.dto.request;

public class FreezeUnfreezeRequestDto {

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
