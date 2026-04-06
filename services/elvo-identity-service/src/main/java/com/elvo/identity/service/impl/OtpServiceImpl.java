package com.elvo.identity.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.entity.VerificationOtp;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.VerificationOtpRepository;
import com.elvo.identity.security.OtpCryptoService;
import com.elvo.identity.service.EmailSenderService;
import com.elvo.identity.service.OtpRateLimitService;
import com.elvo.identity.service.OtpService;
import com.elvo.identity.service.SmsSenderService;
import com.elvo.identity.service.VerificationPolicyService;

@Service
public class OtpServiceImpl implements OtpService {

    private final VerificationOtpRepository verificationOtpRepository;
    private final VerificationPolicyService verificationPolicyService;
    private final OtpRateLimitService otpRateLimitService;
    private final OtpCryptoService otpCryptoService;
    private final EmailSenderService emailSenderService;
    private final SmsSenderService smsSenderService;
    private final AuditRepository auditRepository;
    private final AuditEventPublisher auditEventPublisher;

    public OtpServiceImpl(VerificationOtpRepository verificationOtpRepository,
                          VerificationPolicyService verificationPolicyService,
                          OtpRateLimitService otpRateLimitService,
                          OtpCryptoService otpCryptoService,
                          EmailSenderService emailSenderService,
                          SmsSenderService smsSenderService,
                          AuditRepository auditRepository,
                          AuditEventPublisher auditEventPublisher) {
        this.verificationOtpRepository = verificationOtpRepository;
        this.verificationPolicyService = verificationPolicyService;
        this.otpRateLimitService = otpRateLimitService;
        this.otpCryptoService = otpCryptoService;
        this.emailSenderService = emailSenderService;
        this.smsSenderService = smsSenderService;
        this.auditRepository = auditRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public OtpDispatchResult issueVerificationOtp(User user,
                                                  VerificationOtp.Channel channel,
                                                  VerificationOtp.Purpose purpose,
                                                  String destination,
                                                  boolean resend,
                                                  String requestId,
                                                  String correlationId,
                                                  String sourceIp,
                                                  String deviceId) {
        String effectiveRequestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
        Instant now = Instant.now();

        if (resend) {
            otpRateLimitService.enforceSendLimit(purpose, sourceIp, deviceId);
            long recentOtpCount = verificationOtpRepository.countByUserAndPurposeSince(
                    user.getId(),
                    purpose,
                    now.minus(verificationPolicyService.resendWindow()));
            if (recentOtpCount >= verificationPolicyService.maxResendsPerWindow() + 1L) {
                recordOtpAudit(user, "OTP_RESEND_LIMIT_EXCEEDED|purpose=" + purpose.name());
                throw new IllegalStateException("OTP resend limit reached");
            }
        }

        List<VerificationOtp> activeOtps = verificationOtpRepository.lockActiveOtps(user.getId(), purpose);
        VerificationOtp latestActiveOtp = activeOtps.isEmpty() ? null : activeOtps.get(0);

        if (resend && latestActiveOtp != null
                && now.isBefore(latestActiveOtp.getCreatedAt().plus(verificationPolicyService.resendCooldown()))) {
            recordOtpAudit(user, "OTP_RESEND_COOLDOWN_ACTIVE|purpose=" + purpose.name());
            throw new IllegalStateException("OTP resend cooldown active");
        }

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
        otp.setExpiresAt(now.plus(verificationPolicyService.otpTtl()));
        otp.setOtpHash(otpCryptoService.hashOtp(user.getId(), purpose, otp.getDestination(), otpCode, effectiveRequestId));
        otp.setStatus(VerificationOtp.Status.ACTIVE);
        otp.setAttemptCount(0);
        otp.setResendCount(resend && latestActiveOtp != null ? latestActiveOtp.getResendCount() + 1 : 0);
        VerificationOtp savedOtp = verificationOtpRepository.save(otp);
        recordOtpAudit(user, "OTP_GENERATED|purpose=" + purpose.name() + "|channel=" + channel.name());

        dispatchOtp(savedOtp, otpCode);
        recordOtpAudit(user, "OTP_DELIVERED|purpose=" + purpose.name() + "|channel=" + channel.name());
        return new OtpDispatchResult(savedOtp.getRequestId(), maskDestination(savedOtp), savedOtp.getExpiresAt());
    }

    @Override
    @Transactional
    public OtpVerificationResult verifyOtp(User user,
                                           VerificationOtp.Purpose purpose,
                                           String otpCode,
                                           String requestId,
                                           String sourceIp,
                                           String deviceId) {
        otpRateLimitService.enforceVerifyLimit(purpose, sourceIp, deviceId);

        VerificationOtp otp = resolveActiveOtp(user.getId(), purpose, requestId);
        if (otp == null) {
            recordOtpAudit(user, "OTP_VERIFY_FAILED|reason=MISSING|purpose=" + purpose.name());
            return new OtpVerificationResult(false, "OTP_REQUIRED", "Verification code is required");
        }

        if (otp.getStatus() != VerificationOtp.Status.ACTIVE) {
            recordOtpAudit(user, "OTP_VERIFY_FAILED|reason=STATUS_INVALID|purpose=" + purpose.name());
            return new OtpVerificationResult(false, "OTP_INVALID", "Verification code is invalid");
        }

        if (otp.getConsumedAt() != null) {
            recordOtpAudit(user, "OTP_VERIFY_FAILED|reason=ALREADY_USED|purpose=" + purpose.name());
            return new OtpVerificationResult(false, "OTP_ALREADY_USED", "Verification code is already used");
        }

        Instant now = Instant.now();
        if (now.isAfter(otp.getExpiresAt())) {
            otp.setStatus(VerificationOtp.Status.EXPIRED);
            recordOtpAudit(user, "OTP_EXPIRED|purpose=" + purpose.name());
            return new OtpVerificationResult(false, "OTP_EXPIRED", "Verification code expired");
        }

        boolean matched = otpCryptoService.matches(user.getId(), purpose, otp.getDestination(), otp.getRequestId(), otpCode, otp.getOtpHash());
        if (!matched) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            if (otp.getAttemptCount() >= verificationPolicyService.maxOtpAttempts()) {
                otp.setStatus(VerificationOtp.Status.LOCKED);
                recordOtpAudit(user, "OTP_LOCKED|purpose=" + purpose.name());
                return new OtpVerificationResult(false, "OTP_LOCKED", "Verification code locked");
            }
            recordOtpAudit(user, "OTP_VERIFY_FAILED|reason=INVALID|purpose=" + purpose.name());
            return new OtpVerificationResult(false, "OTP_INVALID", "Verification code is invalid");
        }

        otp.setStatus(VerificationOtp.Status.USED);
        otp.setConsumedAt(now);
        recordOtpAudit(user, "OTP_VERIFIED|purpose=" + purpose.name());
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

    private void recordOtpAudit(User user, String description) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.USER_ACTIVITY);
        audit.setDescription(description);
        audit.setSourceType(Audit.SourceType.SYSTEM);
        audit.setUser(user);
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);
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
