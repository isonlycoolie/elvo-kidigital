package com.elvo.identity.dto.response;

import java.util.UUID;

public record RegistrationResponse(
        UUID userId,
        String ean,
        String email,
        String phone,
        boolean mfaEnabled
) {
}
