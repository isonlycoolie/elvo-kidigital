package com.elvo.identity.dto.response;

public record FastLoginResponse(
        boolean authenticated,
        String method,
        SessionTokenResponse session
) {
}
