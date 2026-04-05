package com.elvo.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;

public class EtcRedeemRequestDto {

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    public EtcRedeemRequestDto() {
    }

    public EtcRedeemRequestDto(String code, String idempotencyKey) {
        this.code = code;
        this.idempotencyKey = idempotencyKey;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
