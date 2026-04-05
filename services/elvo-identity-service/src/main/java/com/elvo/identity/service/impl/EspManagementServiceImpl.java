package com.elvo.identity.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.dto.request.EspGenerateRequest;
import com.elvo.identity.dto.request.EspVerifyRequest;
import com.elvo.identity.dto.response.EspGenerateResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.EspManagementService;

@Service
public class EspManagementServiceImpl implements EspManagementService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration ESP_TTL = Duration.ofMinutes(10);
    private static final Duration ESP_RATE_LIMIT = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public EspManagementServiceImpl(UserRepository userRepository, AuditRepository auditRepository) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    @Transactional
    public EspGenerateResponse generateEsp(EspGenerateRequest request) {
        User user = loadUser(request.getUserId());
        enforceRateLimit(user);
        return issueEsp(user, request, "ESP generated");
    }

    @Override
    @Transactional
    public EspGenerateResponse updateEsp(EspGenerateRequest request) {
        User user = loadUser(request.getUserId());
        enforceRateLimit(user);
        return issueEsp(user, request, "ESP updated");
    }

    @Override
    @Transactional
    public boolean verifyEsp(EspVerifyRequest request) {
        User user = loadUser(request.getUserId());

        if (user.getEspHash() == null || user.getEspExpiresAt() == null || Instant.now().isAfter(user.getEspExpiresAt())) {
            logAudit(user, "ESP verification failed: code missing or expired", request.getSourceIp(), request.getSourceUserAgent());
            return false;
        }

        if (user.getEspFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            logAudit(user, "ESP verification blocked: too many failed attempts", request.getSourceIp(), request.getSourceUserAgent());
            return false;
        }

        boolean match = passwordEncoder.matches(request.getEspCode(), user.getEspHash());
        if (!match) {
            user.setEspFailedAttempts(user.getEspFailedAttempts() + 1);
            userRepository.save(user);
            logAudit(user, "ESP verification failed", request.getSourceIp(), request.getSourceUserAgent());
            return false;
        }

        user.setEspEnabled(true);
        user.setEspFailedAttempts(0);
        userRepository.save(user);
        logAudit(user, "ESP verified successfully", request.getSourceIp(), request.getSourceUserAgent());
        return true;
    }

    private EspGenerateResponse issueEsp(User user, EspGenerateRequest request, String description) {
        String rawCode = generateCode();
        user.setEspHash(passwordEncoder.encode(rawCode));
        user.setEspExpiresAt(Instant.now().plus(ESP_TTL));
        user.setEspFailedAttempts(0);
        user.setEspLastRequestedAt(Instant.now());
        userRepository.save(user);

        logAudit(user, description, request.getSourceIp(), request.getSourceUserAgent());
        return new EspGenerateResponse(rawCode, user.getEspExpiresAt());
    }

    private User loadUser(java.util.UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void enforceRateLimit(User user) {
        Instant lastRequestedAt = user.getEspLastRequestedAt();
        if (lastRequestedAt != null && Instant.now().isBefore(lastRequestedAt.plus(ESP_RATE_LIMIT))) {
            throw new IllegalStateException("ESP generation rate limit exceeded");
        }
    }

    private String generateCode() {
        int value = secureRandom.nextInt(900_000) + 100_000;
        return String.valueOf(value);
    }

    private void logAudit(User user, String description, String sourceIp, String sourceUserAgent) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.ESP_CHANGE);
        audit.setDescription(description);
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        audit.setUser(user);
        auditRepository.save(audit);
    }
}
