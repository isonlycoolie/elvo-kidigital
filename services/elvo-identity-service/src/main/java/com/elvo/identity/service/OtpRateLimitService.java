package com.elvo.identity.service;

import com.elvo.identity.entity.VerificationOtp;

public interface OtpRateLimitService {

    void enforceSendLimit(VerificationOtp.Purpose purpose, String sourceIp, String deviceId);

    void enforceVerifyLimit(VerificationOtp.Purpose purpose, String sourceIp, String deviceId);
}
