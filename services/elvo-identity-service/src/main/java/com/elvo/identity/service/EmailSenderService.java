package com.elvo.identity.service;

import java.time.Duration;

public interface EmailSenderService {

    void sendVerificationOtp(String destinationEmail, String otpCode, Duration ttl, String requestId);
}
