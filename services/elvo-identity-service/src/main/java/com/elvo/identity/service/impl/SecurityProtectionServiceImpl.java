package com.elvo.identity.service.impl;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.SecurityProtectionService;

@Service
public class SecurityProtectionServiceImpl implements SecurityProtectionService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofSeconds(1);

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AuditRepository auditRepository;

    public SecurityProtectionServiceImpl(UserRepository userRepository,
                                         DeviceRepository deviceRepository,
                                         AuditRepository auditRepository) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void enforcePerUserRateLimit(User user) {
        Instant lastEventAt = user.getSecurityLastEventAt();
        if (lastEventAt != null && Instant.now().isBefore(lastEventAt.plus(MIN_REQUEST_INTERVAL))) {
            throw new IllegalStateException("Rate limit exceeded for user");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void ensureAccountNotLocked(User user) {
        if (user.getLockoutUntil() != null && Instant.now().isBefore(user.getLockoutUntil())) {
            throw new IllegalStateException("Account is temporarily locked");
        }
    }

    @Override
    @Transactional
    public void recordFailedAuthentication(User user, String sourceIp, String sourceUserAgent, String deviceId) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        user.setSecurityLastEventAt(Instant.now());

        if (user.getFailedLoginAttempts() >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setLockoutUntil(Instant.now().plus(LOCKOUT_DURATION));
            user.setSuspiciousActivityCount(user.getSuspiciousActivityCount() + 1);
            markDeviceSuspicious(user.getId(), deviceId);
        }

        userRepository.save(user);

        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.USER_ACTIVITY);
        audit.setDescription("AUTH_FAILURE|flow=login|reason=INVALID_CREDENTIALS");
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        audit.setUser(user);
        auditRepository.save(audit);
    }

    @Override
    @Transactional
    public void recordSuccessfulAuthentication(User user, String sourceIp, String sourceUserAgent, String deviceId) {
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user.setSecurityLastEventAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.USER_ACTIVITY);
        audit.setDescription("Successful authentication event");
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        audit.setUser(user);
        auditRepository.save(audit);

        deviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId).ifPresent(device -> {
            if (device.isSuspicious()) {
                device.setSuspicious(false);
                deviceRepository.save(device);
            }
        });
    }

    private void markDeviceSuspicious(java.util.UUID userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }

        deviceRepository.findByUserIdAndDeviceId(userId, deviceId).ifPresent(device -> {
            device.setSuspicious(true);
            deviceRepository.save(device);
        });
    }
}
