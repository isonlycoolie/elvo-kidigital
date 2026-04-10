package com.elvo.identity.dto.response;

public record TotpVerificationResponse(
    boolean verified,
    String code,
    String message
) {
}