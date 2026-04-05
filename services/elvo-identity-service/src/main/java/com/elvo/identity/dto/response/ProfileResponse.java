package com.elvo.identity.dto.response;

import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        String ean,
        String email,
        String phone,
        String displayName,
        boolean mfaEnabled,
        boolean espEnabled
) {
}
