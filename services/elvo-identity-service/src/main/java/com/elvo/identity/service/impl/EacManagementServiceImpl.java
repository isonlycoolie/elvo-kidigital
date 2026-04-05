package com.elvo.identity.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.dto.request.EacGenerateRequest;
import com.elvo.identity.dto.request.EacVerifyRequest;
import com.elvo.identity.dto.response.EacGenerateResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.EacManagementService;

@Service
public class EacManagementServiceImpl implements EacManagementService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration EAC_TTL = Duration.ofMinutes(5);
    private static final Duration EAC_RATE_LIMIT = Duration.ofSeconds(20);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final DeviceRepository deviceRepository;
    private final AuditRepository auditRepository;
    private final SecurityHashingService hashingService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EacManagementServiceImpl(UserRepository userRepository,
                                    SessionRepository sessionRepository,
                                    DeviceRepository deviceRepository,
                                    AuditRepository auditRepository,
                                    SecurityHashingService hashingService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.deviceRepository = deviceRepository;
        this.auditRepository = auditRepository;
        this.hashingService = hashingService;
    }

    @Override
    @Transactional
    public EacGenerateResponse generateEac(EacGenerateRequest request) {
        User user = loadVerifiedUser(request.getUserId());
        validateSessionAndDevice(user, request.getSessionId(), request.getDeviceId());
        enforceRateLimit(user);

        String eacCode = generateCode();
        user.setEacHash(hashingService.hashOneTimeCode(eacCode));
        user.setEacExpiresAt(Instant.now().plus(EAC_TTL));
        user.setEacFailedAttempts(0);
        user.setEacLastRequestedAt(Instant.now());
        userRepository.save(user);

        logAudit(user, "EAC generated for action: " + request.getAction(), request.getSourceIp(), request.getSourceUserAgent());
        return new EacGenerateResponse(eacCode, user.getEacExpiresAt());
    }

    @Override
    @Transactional
    public boolean verifyEac(EacVerifyRequest request) {
        User user = loadVerifiedUser(request.getUserId());
        validateSessionAndDevice(user, request.getSessionId(), request.getDeviceId());

        if (user.getEacHash() == null || user.getEacExpiresAt() == null || Instant.now().isAfter(user.getEacExpiresAt())) {
            logAudit(user, "EAC verification failed: code missing or expired", request.getSourceIp(), request.getSourceUserAgent());
            return false;
        }

        if (user.getEacFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            logAudit(user, "EAC verification blocked: too many failed attempts", request.getSourceIp(), request.getSourceUserAgent());
            return false;
        }

        boolean matched = hashingService.verifyOneTimeCode(request.getEacCode().toUpperCase(Locale.ROOT), user.getEacHash());
        if (!matched) {
            user.setEacFailedAttempts(user.getEacFailedAttempts() + 1);
            userRepository.save(user);
            logAudit(user, "EAC verification failed for action: " + request.getAction(), request.getSourceIp(), request.getSourceUserAgent());
            return false;
        }

        user.setEacFailedAttempts(0);
        userRepository.save(user);
        logAudit(user, "EAC verified for action: " + request.getAction(), request.getSourceIp(), request.getSourceUserAgent());
        return true;
    }

    private User loadVerifiedUser(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new IllegalStateException("User account is not active");
        }
        if (!user.isEspEnabled()) {
            throw new IllegalStateException("ESP verification is required before EAC operations");
        }
        return user;
    }

    private void enforceRateLimit(User user) {
        Instant lastRequested = user.getEacLastRequestedAt();
        if (lastRequested != null && Instant.now().isBefore(lastRequested.plus(EAC_RATE_LIMIT))) {
            throw new IllegalStateException("EAC generation rate limit exceeded");
        }
    }

    private void validateSessionAndDevice(User user, java.util.UUID sessionId, java.util.UUID deviceId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (session.isRevoked() || !session.isActive() || !session.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Session is not valid for this user");
        }

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
        if (device.isRevoked() || !device.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Device is not valid for this user");
        }
        if (!device.isTrusted() || device.isSuspicious()) {
            throw new IllegalStateException("Device trust verification failed");
        }

        if (!session.getDevice().getId().equals(device.getId())) {
            throw new IllegalStateException("Session and device do not match");
        }
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private void logAudit(User user, String description, String sourceIp, String sourceUserAgent) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.USER_ACTIVITY);
        audit.setDescription(description);
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        audit.setUser(user);
        auditRepository.save(audit);
    }
}
