package com.elvo.identity.service;

import java.time.Duration;

public interface SmsSenderService {

    void sendVerificationOtp(String destinationPhone, String otpCode, Duration ttl, String requestId);
}
