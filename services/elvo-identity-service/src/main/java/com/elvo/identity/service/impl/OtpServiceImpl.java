package com.elvo.identity.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.User;
import com.elvo.identity.entity.VerificationOtp;
import com.elvo.identity.repository.VerificationOtpRepository;
import com.elvo.identity.security.OtpCryptoService;
import com.elvo.identity.service.EmailSenderService;
import com.elvo.identity.service.OtpService;
import com.elvo.identity.service.SmsSenderService;
import com.elvo.identity.service.VerificationPolicyService;

@Service
public class OtpServiceImpl implements OtpService {

    private final VerificationOtpRepository verificationOtpRepository;
    private final VerificationPolicyService verificationPolicyService;
    private final OtpCryptoService otpCryptoService;
    private final EmailSenderService emailSenderService;
    private final SmsSenderService smsSenderService;

    public OtpServiceImpl(VerificationOtpRepository verificationOtpRepository,
                          VerificationPolicyService verificationPolicyService,
                          OtpCryptoService otpCryptoService,
                          EmailSenderService emailSenderService,
                          SmsSenderService smsSenderService) {
        this.verificationOtpRepository = verificationOtpRepository;
        this.verificationPolicyService = verificationPolicyService;
        this.otpCryptoService = otpCryptoService;
        this.emailSenderService = emailSenderService;
        this.smsSenderService = smsSenderService;
    }

    @Override
    @Transactional
    public OtpDispatchResult issueVerificationOtp(User user,
                                                  VerificationOtp.Channel channel,
                                                  VerificationOtp.Purpose purpose,
                                                  String destination,
                                                  String requestId,
                                                  String correlationId) {
        String effectiveRequestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();

        List<VerificationOtp> activeOtps = verificationOtpRepository.lockActiveOtps(user.getId(), purpose);
        for (VerificationOtp activeOtp : activeOtps) {
            activeOtp.setStatus(VerificationOtp.Status.REVOKED);
        }

        String otpCode = otpCryptoService.generateSixDigitCode();
        VerificationOtp otp = new VerificationOtp();
        otp.setUserId(user.getId());
        otp.setChannel(channel);
        otp.setPurpose(purpose);
        otp.setDestination(normalizeDestination(channel, destination));
        otp.setRequestId(effectiveRequestId);
        otp.setCorrelationId(correlationId);
        otp.setExpiresAt(Instant.now().plus(verificationPolicyService.otpTtl()));
        otp.setOtpHash(otpCryptoService.hashOtp(user.getId(), purpose, otp.getDestination(), otpCode, effectiveRequestId));
        otp.setStatus(VerificationOtp.Status.ACTIVE);
        otp.setAttemptCount(0);
        otp.setResendCount(0);
        VerificationOtp savedOtp = verificationOtpRepository.save(otp);

        dispatchOtp(savedOtp, otpCode);
        return new OtpDispatchResult(savedOtp.getRequestId(), maskDestination(savedOtp), savedOtp.getExpiresAt());
    }

    @Override
    @Transactional
    public OtpVerificationResult verifyOtp(User user,
                                           VerificationOtp.Purpose purpose,
                                           String otpCode,
                                           String requestId) {
        VerificationOtp otp = resolveActiveOtp(user.getId(), purpose, requestId);
        if (otp == null) {
            return new OtpVerificationResult(false, "OTP_REQUIRED", "Verification code is required");
        }

        if (otp.getStatus() != VerificationOtp.Status.ACTIVE) {
            return new OtpVerificationResult(false, "OTP_INVALID", "Verification code is invalid");
        }

        if (otp.getConsumedAt() != null) {
            return new OtpVerificationResult(false, "OTP_ALREADY_USED", "Verification code is already used");
        }

        Instant now = Instant.now();
        if (now.isAfter(otp.getExpiresAt())) {
            otp.setStatus(VerificationOtp.Status.EXPIRED);
            return new OtpVerificationResult(false, "OTP_EXPIRED", "Verification code expired");
        }

        boolean matched = otpCryptoService.matches(user.getId(), purpose, otp.getDestination(), otp.getRequestId(), otpCode, otp.getOtpHash());
        if (!matched) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            if (otp.getAttemptCount() >= verificationPolicyService.maxOtpAttempts()) {
                otp.setStatus(VerificationOtp.Status.LOCKED);
                return new OtpVerificationResult(false, "OTP_LOCKED", "Verification code locked");
            }
            return new OtpVerificationResult(false, "OTP_INVALID", "Verification code is invalid");
        }

        otp.setStatus(VerificationOtp.Status.USED);
        otp.setConsumedAt(now);
        return new OtpVerificationResult(true, "OTP_VERIFIED", "Verification successful");
    }

    private VerificationOtp resolveActiveOtp(UUID userId, VerificationOtp.Purpose purpose, String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            return verificationOtpRepository.lockByRequestId(userId, purpose, requestId.trim()).orElse(null);
        }
        return verificationOtpRepository.findFirstByUserIdAndPurposeAndStatusOrderByCreatedAtDesc(
                userId,
                purpose,
                VerificationOtp.Status.ACTIVE)
                .orElse(null);
    }

    private void dispatchOtp(VerificationOtp otp, String otpCode) {
        if (otp.getChannel() == VerificationOtp.Channel.EMAIL) {
            emailSenderService.sendVerificationOtp(otp.getDestination(), otpCode, verificationPolicyService.otpTtl(), otp.getRequestId());
            return;
        }
        smsSenderService.sendVerificationOtp(otp.getDestination(), otpCode, verificationPolicyService.otpTtl(), otp.getRequestId());
    }

    private String normalizeDestination(VerificationOtp.Channel channel, String destination) {
        if (channel == VerificationOtp.Channel.EMAIL) {
            return destination.trim().toLowerCase(Locale.ROOT);
        }
        return destination.trim();
    }

    private String maskDestination(VerificationOtp otp) {
        if (otp.getChannel() == VerificationOtp.Channel.EMAIL) {
            String value = otp.getDestination();
            int atIndex = value.indexOf('@');
            if (atIndex <= 1) {
                return "***";
            }
            return value.charAt(0) + "***" + value.substring(atIndex);
        }
        String value = otp.getDestination();
        if (value.length() < 4) {
            return "***";
        }
        return "***" + value.substring(value.length() - 2);
    }
}
