package com.elvo.identity.service;

import java.util.List;
import java.util.UUID;

public interface RecoveryCodeService {

    RecoveryCodeBatch issueCodes(UUID userId, String totpCode);

    boolean consumeCode(UUID userId, String code);

    int remainingCodes(UUID userId);

    record RecoveryCodeBatch(List<String> codes, int remainingCount) {
    }
}