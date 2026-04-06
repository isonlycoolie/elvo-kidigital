package com.elvo.wallet.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class WalletIdempotencyService {

    private static final String LEGACY_SCOPE = "legacy";

    private final ConcurrentHashMap<String, IdempotencyEntry> completedOperations = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public WalletIdempotencyService(@Value("${elvo.security.idempotency.ttl-seconds:900}") long ttlSeconds) {
        this.ttlSeconds = Math.max(60L, ttlSeconds);
    }

    public WalletIdempotencyService() {
        this(900L);
    }

    public Optional<WalletFlowResult> get(String key) {
        return get(key, LEGACY_SCOPE, LEGACY_SCOPE, LEGACY_SCOPE);
    }

    public Optional<WalletFlowResult> get(String key, String userScope, String endpointScope, String payloadFingerprint) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        IdempotencyEntry entry = completedOperations.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.expiresAt().isBefore(Instant.now())) {
            completedOperations.remove(key, entry);
            return Optional.empty();
        }

        if (!entry.matches(userScope, endpointScope, payloadFingerprint)) {
            throw new IllegalArgumentException("Idempotency key cannot be reused with a different request context");
        }

        return Optional.of(entry.result());
    }

    public void put(String key, WalletFlowResult result) {
        put(key, LEGACY_SCOPE, LEGACY_SCOPE, LEGACY_SCOPE, result);
    }

    public void put(String key,
                    String userScope,
                    String endpointScope,
                    String payloadFingerprint,
                    WalletFlowResult result) {
        if (key == null || key.isBlank() || result == null) {
            return;
        }

        IdempotencyEntry candidate = new IdempotencyEntry(
                safe(userScope),
                safe(endpointScope),
                safe(payloadFingerprint),
                result,
                Instant.now().plusSeconds(ttlSeconds));

        completedOperations.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(Instant.now())) {
                return candidate;
            }
            if (!existing.matches(candidate.userScope(), candidate.endpointScope(), candidate.payloadFingerprint())) {
                throw new IllegalArgumentException("Idempotency key cannot be reused with a different request context");
            }
            return existing;
        });
    }

    public String hashPayload(String payloadSource) {
        return hashPayloadValue(payloadSource);
    }

    public static String hashPayloadValue(String payloadSource) {
        String source = payloadSource == null ? "" : payloadSource;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for idempotency payload hashing", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record IdempotencyEntry(
            String userScope,
            String endpointScope,
            String payloadFingerprint,
            WalletFlowResult result,
            Instant expiresAt
    ) {
        boolean matches(String userScope, String endpointScope, String payloadFingerprint) {
            return this.userScope.equals(userScope == null ? "" : userScope)
                    && this.endpointScope.equals(endpointScope == null ? "" : endpointScope)
                    && this.payloadFingerprint.equals(payloadFingerprint == null ? "" : payloadFingerprint);
        }
    }
}
