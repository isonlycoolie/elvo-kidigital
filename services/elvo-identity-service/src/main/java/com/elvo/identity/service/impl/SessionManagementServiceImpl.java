package com.elvo.identity.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.SessionCreateRequest;
import com.elvo.identity.dto.response.SessionInfoResponse;
import com.elvo.identity.dto.response.SessionTokenResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.IdentityAccountReadService;
import com.elvo.identity.service.SessionManagementService;
import com.elvo.identity.util.TokenService;

@Service
public class SessionManagementServiceImpl implements SessionManagementService {

    private static final int MAX_ACTIVE_SESSIONS_PER_USER = 5;

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final SessionRepository sessionRepository;
    private final AuditRepository auditRepository;
    private final TokenService tokenService;
    private final AuditEventPublisher auditEventPublisher;
    private final IdentityAccountReadService accountReadService;

    public SessionManagementServiceImpl(UserRepository userRepository,
                                        DeviceRepository deviceRepository,
                                        SessionRepository sessionRepository,
                                        AuditRepository auditRepository,
                                        TokenService tokenService,
                                        AuditEventPublisher auditEventPublisher,
                                        IdentityAccountReadService accountReadService) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.auditRepository = auditRepository;
        this.tokenService = tokenService;
        this.auditEventPublisher = auditEventPublisher;
        this.accountReadService = accountReadService;
    }

    @Override
    @Transactional
    public SessionTokenResponse createSession(SessionCreateRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Device device = deviceRepository.findByUserIdAndDeviceId(user.getId(), request.getDeviceId())
                .orElseThrow(() -> new IllegalStateException("Device must be registered before session creation"));

        if (device.isRevoked() || device.isSuspicious() || !device.isTrusted()) {
            throw new IllegalStateException("Device trust verification failed");
        }

        device.setDeviceType(request.getDeviceType());
        device.setLastUsedAt(Instant.now());
        Device savedDevice = deviceRepository.save(device);

        String resolvedEan = accountReadService.resolveEan(user.getId());
        TokenService.TokenPayload accessToken = tokenService.generateAccessToken(user.getId(), resolvedEan);
        Instant absoluteSessionExpiresAt = tokenService.calculateSessionAbsoluteExpiry();
        TokenService.TokenPayload refreshToken = tokenService.generateRefreshToken(user.getId(), absoluteSessionExpiresAt);

        Session session = new Session();
        session.setUser(user);
        session.setDevice(savedDevice);
        session.setJwtToken(accessToken.token());
        session.setRefreshToken(refreshToken.token());
        session.setExpiresAt(refreshToken.expiresAt());
        session.setAbsoluteExpiresAt(absoluteSessionExpiresAt);
        session.setIpAddress(request.getSourceIp());
        session.setSessionStatus(Session.SessionStatus.ACTIVE);
        session.setActive(true);
        session.setRevoked(false);
        Session savedSession = sessionRepository.save(session);

        enforceSessionLimit(user.getId());

        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.LOGIN);
        audit.setDescription("Session created for user");
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(request.getSourceIp());
        audit.setSourceUserAgent(request.getSourceUserAgent());
        audit.setSessionId(savedSession.getId());
        audit.setDeviceId(savedDevice.getId());
        audit.setUser(user);
        auditRepository.save(audit);

        return new SessionTokenResponse(
                savedSession.getId(),
                accessToken.token(),
                refreshToken.token(),
                accessToken.expiresAt(),
                refreshToken.expiresAt());
    }

    @Override
    @Transactional
    public int revokeSession(UUID sessionId) {
        int updated = sessionRepository.revokeSession(sessionId);
        if (updated > 0) {
            logSessionRevocation("Session revoked", sessionId, null, null, null);
        }
        return updated;
    }

    @Override
    @Transactional
    public int revokeAllUserSessions(UUID userId) {
        int updated = sessionRepository.revokeActiveSessionsByUserId(userId);
        if (updated > 0) {
            logSessionRevocation("All active sessions revoked", null, userId, null, null);
        }
        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionInfoResponse> getActiveSessions(UUID userId) {
        return sessionRepository.findByUserIdAndActiveTrueAndRevokedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(session -> new SessionInfoResponse(
                        session.getId(),
                        session.getUser().getId(),
                        session.getDevice().getId(),
                        session.getIpAddress(),
                        session.getCreatedAt(),
                        session.getExpiresAt(),
                        session.isActive(),
                        session.isRevoked(),
                        session.getSessionStatus().name()))
                .toList();
    }

    private void enforceSessionLimit(UUID userId) {
        List<Session> activeSessions = sessionRepository.findByUserIdAndActiveTrueAndRevokedFalseOrderByCreatedAtDesc(userId);
        if (activeSessions.size() <= MAX_ACTIVE_SESSIONS_PER_USER) {
            return;
        }

        activeSessions.stream()
                .skip(MAX_ACTIVE_SESSIONS_PER_USER)
                .forEach(session -> {
                    sessionRepository.revokeSession(session.getId());
                    logSessionRevocation("Session revoked due to multi-device limit", session.getId(), userId, session.getDevice().getId(), session.getIpAddress());
                });
    }

    private void logSessionRevocation(String description,
                                      UUID sessionId,
                                      UUID userId,
                                      UUID deviceId,
                                      String sourceIp) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.SESSION_REVOCATION);
        audit.setDescription(description);
        audit.setSourceType(Audit.SourceType.SYSTEM);
        audit.setSourceIp(sourceIp);
        audit.setSessionId(sessionId);
        audit.setDeviceId(deviceId);
        if (userId != null) {
            userRepository.findById(userId).ifPresent(audit::setUser);
        }
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);
    }
}
