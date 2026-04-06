package com.elvo.identity.dto.response;

public record OtpVerificationResponse(
        boolean verified,
        String code,
        String message
) {
}
