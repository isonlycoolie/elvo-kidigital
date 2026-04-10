package com.elvo.identity.service;

import java.time.Instant;
import java.util.UUID;

public interface TotpManagementService {

    Enrollment startEnrollment(UUID userId);

    boolean confirmEnrollment(UUID userId, String code);

    boolean verifyActiveCode(UUID userId, String code);

    record Enrollment(String secret, String otpauthUrl, Instant expiresAt) {
    }
}