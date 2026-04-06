package com.elvo.wallet.service;

import java.util.UUID;

public interface EacReplayProtectionService {

    EacValidationResult validateAndConsume(UUID userId, String eacCode, String requestBinding);

    record EacValidationResult(boolean accepted, String message) {
        public static EacValidationResult allow() {
            return new EacValidationResult(true, "accepted");
        }

        public static EacValidationResult deny(String message) {
            return new EacValidationResult(false, message);
        }
    }
}