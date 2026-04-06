package com.elvo.wallet.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.wallet.service.EacReplayProtectionService;

@Service
public class InMemoryEacReplayProtectionService implements EacReplayProtectionService {

    private final Map<String, ConsumedEac> consumedEacs = new ConcurrentHashMap<>();
    private final long replayWindowSeconds;

    public InMemoryEacReplayProtectionService(
            @Value("${elvo.security.eac.replay.window-seconds:300}") long replayWindowSeconds
    ) {
        this.replayWindowSeconds = replayWindowSeconds;
    }

    @Override
    public EacValidationResult validateAndConsume(UUID userId, String eacCode, String requestBinding) {
        if (userId == null || eacCode == null || eacCode.isBlank() || requestBinding == null || requestBinding.isBlank()) {
            return EacValidationResult.deny("Invalid EAC replay context");
        }

        Instant now = Instant.now();
        String nonceKey = digest(userId + ":" + eacCode);
        String bindingHash = digest(requestBinding);
        cleanupExpired(now);

        ConsumedEac existing = consumedEacs.get(nonceKey);
        if (existing != null) {
            if (now.isAfter(existing.expiresAt())) {
                return EacValidationResult.deny("EAC expired");
            }
            if (!existing.requestBindingHash().equals(bindingHash)) {
                return EacValidationResult.deny("EAC request binding mismatch");
            }
            return EacValidationResult.deny("EAC replay detected");
        }

        Instant expiresAt = now.plusSeconds(replayWindowSeconds);
        consumedEacs.put(nonceKey, new ConsumedEac(bindingHash, expiresAt));
        return EacValidationResult.allow();
    }

    private void cleanupExpired(Instant now) {
        consumedEacs.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private record ConsumedEac(String requestBindingHash, Instant expiresAt) {
    }
}