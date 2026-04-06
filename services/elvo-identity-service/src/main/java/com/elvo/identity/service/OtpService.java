package com.elvo.identity.service;

import java.time.Instant;
import java.util.UUID;

import com.elvo.identity.entity.User;
import com.elvo.identity.entity.VerificationOtp;

public interface OtpService {

    OtpDispatchResult issueVerificationOtp(User user,
                                           VerificationOtp.Channel channel,
                                           VerificationOtp.Purpose purpose,
                                           String destination,
                             boolean resend,
                                           String requestId,
                             String correlationId,
                             String sourceIp,
                             String deviceId);

    OtpVerificationResult verifyOtp(User user,
                                    VerificationOtp.Purpose purpose,
                                    String otpCode,
                         String requestId,
                         String sourceIp,
                         String deviceId);

    record OtpDispatchResult(String requestId, String maskedDestination, Instant expiresAt) {
    }

    record OtpVerificationResult(boolean success, String code, String message) {
    }
}
