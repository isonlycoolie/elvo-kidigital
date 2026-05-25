package com.elvo.wallet.service;

import java.time.Instant;
import java.util.UUID;

public interface ChallengeCodeService {

    IssueResult issueCode(UUID walletId, Instant expiresAt, int maxUsageCount);

    ValidationResult validateCode(UUID walletId, String rawCode);

    int expireCodes(Instant now);

    record IssueResult(UUID challengeCodeId, String plainCode, Instant expiresAt) {
    }

    record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
