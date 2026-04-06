package com.elvo.identity.service;

import java.time.Duration;

public interface VerificationPolicyService {

    Duration otpTtl();

    int maxOtpAttempts();

    int maxResendsPerWindow();

    Duration resendWindow();

    Duration resendCooldown();
}
