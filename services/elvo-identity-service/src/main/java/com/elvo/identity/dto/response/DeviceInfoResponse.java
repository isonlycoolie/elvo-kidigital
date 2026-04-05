package com.elvo.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeviceInfoResponse(
        UUID deviceId,
        String externalDeviceId,
        String deviceType,
        boolean trusted,
        boolean revoked,
        boolean suspicious,
        Instant lastUsedAt
) {
}
