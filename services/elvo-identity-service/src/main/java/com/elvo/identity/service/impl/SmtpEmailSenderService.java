package com.elvo.identity.service.impl;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.elvo.identity.service.EmailSenderService;

@Service
public class SmtpEmailSenderService implements EmailSenderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailSenderService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSenderService(JavaMailSender mailSender,
                                  @Value("${elvo.communication.email.from:${EMAIL_PROVIDER_FROM:no-reply@elvo.local}}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendVerificationOtp(String destinationEmail, String otpCode, Duration ttl, String requestId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(destinationEmail);
        message.setSubject("ELVO verification code");
        message.setText(buildBody(otpCode, ttl));
        mailSender.send(message);

        LOGGER.info("OTP email sent requestId={} destination={}", requestId, maskEmail(destinationEmail));
    }

    private String buildBody(String otpCode, Duration ttl) {
        long minutes = Math.max(1, ttl.toMinutes());
        return "Your ELVO verification code is " + otpCode + ". It expires in " + minutes + " minutes. "
                + "If you did not request this code, ignore this message.";
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
