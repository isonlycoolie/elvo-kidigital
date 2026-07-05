package com.elvo.identity.service.impl;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.elvo.identity.client.NotificationDeliveryClient;
import com.elvo.identity.service.EmailSenderService;

@Service
@Primary
@ConditionalOnProperty(name = "elvo.communication.notification.enabled", havingValue = "true")
public class NotificationEmailSenderService implements EmailSenderService {

    private final NotificationDeliveryClient notificationDeliveryClient;

    public NotificationEmailSenderService(NotificationDeliveryClient notificationDeliveryClient) {
        this.notificationDeliveryClient = notificationDeliveryClient;
    }

    @Override
    public void sendVerificationOtp(String destinationEmail, String otpCode, Duration ttl, String requestId) {
        String message = "Your ELVO verification code is " + otpCode + ". It expires in " + ttl.toMinutes() + " minutes.";
        notificationDeliveryClient.send("EMAIL", destinationEmail, message, "OTP_VERIFY");
    }
}
