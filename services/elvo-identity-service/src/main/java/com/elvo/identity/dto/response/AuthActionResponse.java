package com.elvo.identity.dto.response;

public record AuthActionResponse(
        boolean success,
        String message
) {
}
