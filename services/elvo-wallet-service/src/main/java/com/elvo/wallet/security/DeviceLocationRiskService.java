package com.elvo.wallet.security;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeviceLocationRiskService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.risk.device-location");

    private final Map<UUID, ContextSnapshot> trustedContexts = new ConcurrentHashMap<>();

    public boolean requiresAdditionalVerification(UUID userId, String deviceId, String locationHint) {
        if (userId == null) {
            return false;
        }

        String normalizedDevice = normalize(deviceId);
        String normalizedLocation = normalize(locationHint);
        ContextSnapshot current = new ContextSnapshot(normalizedDevice, normalizedLocation, Instant.now());
        ContextSnapshot previous = trustedContexts.putIfAbsent(userId, current);
        if (previous == null) {
            return false;
        }

        boolean deviceChanged = normalizedDevice != null && previous.deviceId() != null
                && !normalizedDevice.equals(previous.deviceId());
        boolean locationChanged = normalizedLocation != null && previous.locationHint() != null
                && !normalizedLocation.equals(previous.locationHint());

        boolean risky = deviceChanged || locationChanged;
        if (risky) {
            AUDIT_LOG.warn("wallet_device_location_risk userId={} previousDevice={} currentDevice={} previousLocation={} currentLocation={} lastSeen={}",
                    userId,
                    previous.deviceId(),
                    normalizedDevice,
                    previous.locationHint(),
                    normalizedLocation,
                    previous.seenAt());
            return true;
        }

        trustedContexts.put(userId, current);
        return false;
    }

    public void markTrusted(UUID userId, String deviceId, String locationHint) {
        if (userId == null) {
            return;
        }
        trustedContexts.put(userId, new ContextSnapshot(normalize(deviceId), normalize(locationHint), Instant.now()));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ContextSnapshot(String deviceId, String locationHint, Instant seenAt) {
    }
}