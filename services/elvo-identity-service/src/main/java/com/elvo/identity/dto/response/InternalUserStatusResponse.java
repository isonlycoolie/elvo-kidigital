package com.elvo.identity.dto.response;

import java.util.UUID;

public record InternalUserStatusResponse(
    UUID userId,
    String accountStatus,
    String verificationStatus,
    String registeredPhone,
    boolean mobileVerified
) {
}