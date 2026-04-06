package com.elvo.identity.service.impl;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.identity.service.VerificationPolicyService;

@Service
public class VerificationPolicyServiceImpl implements VerificationPolicyService {

    private final Duration otpTtl;
    private final int maxOtpAttempts;
    private final int maxResendsPerWindow;
    private final Duration resendWindow;
    private final Duration resendCooldown;

    public VerificationPolicyServiceImpl(
            @Value("${elvo.security.otp.ttl-minutes:5}") long otpTtlMinutes,
            @Value("${elvo.security.otp.max-attempts:5}") int maxOtpAttempts,
            @Value("${elvo.security.otp.max-resends-per-window:3}") int maxResendsPerWindow,
            @Value("${elvo.security.otp.resend-window-minutes:15}") long resendWindowMinutes,
            @Value("${elvo.security.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.otpTtl = Duration.ofMinutes(Math.max(1, otpTtlMinutes));
        this.maxOtpAttempts = Math.max(1, maxOtpAttempts);
        this.maxResendsPerWindow = Math.max(1, maxResendsPerWindow);
        this.resendWindow = Duration.ofMinutes(Math.max(1, resendWindowMinutes));
        this.resendCooldown = Duration.ofSeconds(Math.max(1, resendCooldownSeconds));
    }

    @Override
    public Duration otpTtl() {
        return otpTtl;
    }

    @Override
    public int maxOtpAttempts() {
        return maxOtpAttempts;
    }

    @Override
    public int maxResendsPerWindow() {
        return maxResendsPerWindow;
    }

    @Override
    public Duration resendWindow() {
        return resendWindow;
    }

    @Override
    public Duration resendCooldown() {
        return resendCooldown;
    }
}
