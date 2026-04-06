package com.elvo.identity.service.impl;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.elvo.identity.client.SmsProvider;
import com.elvo.identity.service.SmsSenderService;

@Service
public class DefaultSmsSenderService implements SmsSenderService {

    private final SmsProvider smsProvider;

    public DefaultSmsSenderService(SmsProvider smsProvider) {
        this.smsProvider = smsProvider;
    }

    @Override
    public void sendVerificationOtp(String destinationPhone, String otpCode, Duration ttl, String requestId) {
        long minutes = Math.max(1, ttl.toMinutes());
        String message = "Your verification code is " + otpCode + ". It expires in " + minutes + " minutes.";
        smsProvider.sendSms(destinationPhone, message, requestId);
    }
}
