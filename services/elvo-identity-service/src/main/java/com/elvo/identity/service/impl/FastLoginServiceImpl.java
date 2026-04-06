package com.elvo.identity.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.dto.request.FastLoginGenerateRequest;
import com.elvo.identity.dto.request.FastLoginVerifyRequest;
import com.elvo.identity.dto.request.SessionCreateRequest;
import com.elvo.identity.dto.response.FastLoginChallengeResponse;
import com.elvo.identity.dto.response.FastLoginResponse;
import com.elvo.identity.dto.response.SessionTokenResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.FastLoginService;
import com.elvo.identity.service.SessionManagementService;

@Service
public class FastLoginServiceImpl implements FastLoginService {

    private static final Duration PIN_TTL = Duration.ofMinutes(5);
    private static final Duration REQUEST_RATE_LIMIT = Duration.ofSeconds(20);
    private static final int PIN_LENGTH = 6;
    private static final String BIOMETRIC_PREFIX = "BIO:";

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final AuditRepository auditRepository;
    private final SessionManagementService sessionManagementService;
    private final SecurityHashingService hashingService;
    private final SecureRandom secureRandom = new SecureRandom();

    public FastLoginServiceImpl(UserRepository userRepository,
                                DeviceRepository deviceRepository,
                                AuditRepository auditRepository,
                                SessionManagementService sessionManagementService,
                                SecurityHashingService hashingService) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.auditRepository = auditRepository;
        this.sessionManagementService = sessionManagementService;
        this.hashingService = hashingService;
    }

    @Override
    @Transactional
    public FastLoginChallengeResponse generateFastLoginPin(FastLoginGenerateRequest request) {
        User user = loadUser(request.getUserId());
        enforceRateLimit(user);

        String pin = generatePin();
        user.setFastLoginPinHash(hashingService.hashOneTimeCode(pin));
        user.setFastLoginExpiresAt(Instant.now().plus(PIN_TTL));
        user.setFastLoginFailedAttempts(0);
        user.setFastLoginLastRequestedAt(Instant.now());
        userRepository.save(user);

        registerOrRefreshDevice(user, request.getDeviceId(), request.getDeviceType(), request.getSourceIp());
        audit(user, "Fast login PIN generated", request.getSourceIp(), request.getSourceUserAgent());

        return new FastLoginChallengeResponse(pin, user.getFastLoginExpiresAt(), user.isMfaEnabled());
    }

    @Override
    @Transactional
    public FastLoginResponse verifyFastLogin(FastLoginVerifyRequest request) {
        User user = loadUser(request.getUserId());
        Device device = registerOrRefreshDevice(user, request.getDeviceId(), "FAST-LOGIN", request.getSourceIp());

        boolean biometricSuccess = request.getBiometricToken() != null
                && request.getBiometricToken().equals(BIOMETRIC_PREFIX + user.getId() + ":" + device.getDeviceId());
        boolean pinSuccess = request.getPin() != null
                && user.getFastLoginPinHash() != null
                && user.getFastLoginExpiresAt() != null
                && Instant.now().isBefore(user.getFastLoginExpiresAt())
                && hashingService.verifyOneTimeCode(request.getPin(), user.getFastLoginPinHash());

        if (!biometricSuccess && !pinSuccess) {
            user.setFastLoginFailedAttempts(user.getFastLoginFailedAttempts() + 1);
            userRepository.save(user);
            audit(user, "Fast login verification failed", request.getSourceIp(), request.getSourceUserAgent());
            throw new IllegalArgumentException("Fast login verification failed");
        }

        user.setFastLoginPinHash(null);
        user.setFastLoginExpiresAt(null);
        user.setFastLoginFailedAttempts(0);
        userRepository.save(user);

        device.setTrusted(true);
        device.setRevoked(false);
        device.setSuspicious(false);
        device.setLastUsedAt(Instant.now());
        deviceRepository.save(device);

        SessionTokenResponse session = sessionManagementService.createSession(buildSessionRequest(user, device, request));
        audit(user, biometricSuccess ? "Fast login biometric success" : "Fast login PIN success", request.getSourceIp(), request.getSourceUserAgent());

        return new FastLoginResponse(true, biometricSuccess ? "biometric" : "pin", session);
    }

    private User loadUser(java.util.UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void enforceRateLimit(User user) {
        Instant lastRequested = user.getFastLoginLastRequestedAt();
        if (lastRequested != null && Instant.now().isBefore(lastRequested.plus(REQUEST_RATE_LIMIT))) {
            throw new IllegalStateException("Fast login rate limit exceeded");
        }
    }

    private String generatePin() {
        int value = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(value);
    }

    private Device registerOrRefreshDevice(User user, String deviceId, String deviceType, String sourceIp) {
        Device device = deviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId)
                .orElseGet(Device::new);
        device.setUser(user);
        device.setDeviceId(deviceId);
        device.setDeviceType(deviceType);
        device.setTrusted(device.isTrusted());
        device.setRevoked(false);
        device.setSuspicious(false);
        device.setLastUsedAt(Instant.now());
        return deviceRepository.save(device);
    }

    private SessionCreateRequest buildSessionRequest(User user, Device device, FastLoginVerifyRequest request) {
        SessionCreateRequest sessionCreateRequest = new SessionCreateRequest();
        sessionCreateRequest.setUserId(user.getId());
        sessionCreateRequest.setDeviceId(device.getDeviceId());
        sessionCreateRequest.setDeviceType(device.getDeviceType());
        sessionCreateRequest.setSourceIp(request.getSourceIp());
        sessionCreateRequest.setSourceUserAgent(request.getSourceUserAgent());
        return sessionCreateRequest;
    }

    private void audit(User user, String description, String sourceIp, String sourceUserAgent) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.LOGIN);
        audit.setDescription(description);
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        audit.setUser(user);
        auditRepository.save(audit);
    }
}
