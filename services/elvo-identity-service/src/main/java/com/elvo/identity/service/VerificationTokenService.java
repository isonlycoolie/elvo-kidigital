package com.elvo.identity.service;

import java.time.Instant;
import java.util.UUID;

public interface VerificationTokenService {

    VerificationToken issueToken(UUID userId);

    boolean isValidForUser(String token, UUID userId);

    void invalidateForUser(UUID userId);

    record VerificationToken(String token, Instant expiresAt) {
    }
}
