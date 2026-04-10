package com.elvo.identity.service.impl;

import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.dto.response.LoginResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.exception.PendingVerificationException;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.IdentityAccountReadService;
import com.elvo.identity.service.LoginService;
import com.elvo.identity.service.SecurityProtectionService;
import com.elvo.identity.service.TotpManagementService;
import com.elvo.identity.util.TokenService;

@Service
public class LoginServiceImpl implements LoginService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final SessionRepository sessionRepository;
    private final AuditRepository auditRepository;
    private final TokenService tokenService;
    private final SecurityProtectionService securityProtectionService;
    private final SecurityHashingService hashingService;
    private final AuditEventPublisher auditEventPublisher;
    private final IdentityAccountReadService accountReadService;
    private final TotpManagementService totpManagementService;

    public LoginServiceImpl(UserRepository userRepository,
                            DeviceRepository deviceRepository,
                            SessionRepository sessionRepository,
                            AuditRepository auditRepository,
                            TokenService tokenService,
                            SecurityProtectionService securityProtectionService,
                            SecurityHashingService hashingService,
                            AuditEventPublisher auditEventPublisher,
                            IdentityAccountReadService accountReadService,
                            TotpManagementService totpManagementService) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.auditRepository = auditRepository;
        this.tokenService = tokenService;
        this.securityProtectionService = securityProtectionService;
        this.hashingService = hashingService;
        this.auditEventPublisher = auditEventPublisher;
        this.accountReadService = accountReadService;
        this.totpManagementService = totpManagementService;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = null;
        try {
            user = loadUser(request.getIdentifier());
            securityProtectionService.enforcePerUserRateLimit(user);
            securityProtectionService.ensureAccountNotLocked(user);
            validatePassword(user, request.getPassword(), request);
            enforcePendingLifecycle(user, request.getIdentifier());
            validateUserStatus(user);
            enforceChannelVerification(user, request.getIdentifier());
            validateMfaIfRequired(user, request);

            securityProtectionService.recordSuccessfulAuthentication(
                user,
                request.getSourceIp(),
                request.getSourceUserAgent(),
                request.getDeviceId());

            Device device = upsertDevice(user, request);

            String resolvedEan = accountReadService.resolveEan(user.getId());
            TokenService.TokenPayload accessToken = tokenService.generateAccessToken(user.getId(), resolvedEan);
            Instant absoluteSessionExpiresAt = tokenService.calculateSessionAbsoluteExpiry();
            TokenService.TokenPayload refreshToken = tokenService.generateRefreshToken(user.getId(), absoluteSessionExpiresAt);

            Session session = new Session();
            session.setUser(user);
            session.setDevice(device);
            session.setJwtToken(accessToken.token());
            session.setRefreshToken(refreshToken.token());
            session.setExpiresAt(refreshToken.expiresAt());
            session.setAbsoluteExpiresAt(absoluteSessionExpiresAt);
            session.setIpAddress(request.getSourceIp());
            session.setSessionStatus(Session.SessionStatus.ACTIVE);
            session.setActive(true);
            session.setRevoked(false);
            Session savedSession = sessionRepository.save(session);

            device.setLastUsedAt(Instant.now());
            deviceRepository.save(device);

            Audit audit = new Audit();
            audit.setActionType(Audit.ActionType.LOGIN);
            audit.setDescription("User login successful");
            audit.setSourceType(Audit.SourceType.API);
            audit.setSourceIp(request.getSourceIp());
            audit.setSourceUserAgent(request.getSourceUserAgent());
            audit.setCorrelationId(null);
            audit.setSessionId(savedSession.getId());
            audit.setDeviceId(device.getId());
            audit.setUser(user);
            Audit savedAudit = auditRepository.save(audit);
            auditEventPublisher.publish(savedAudit);

            return new LoginResponse(
                    user.getId(),
                    accessToken.token(),
                    refreshToken.token(),
                    accessToken.expiresAt(),
                    refreshToken.expiresAt(),
                    savedSession.getId());
        } catch (RuntimeException ex) {
            // Password failures are already audited by SecurityProtectionService.
            if (user == null || !"Invalid credentials".equals(ex.getMessage())) {
                auditAuthenticationFailure(user, request, classifyFailureReason(ex));
            }
            throw ex;
        }
    }

    private User loadUser(String identifier) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmailIgnoreCase(normalized)
                .or(() -> userRepository.findByPhone(identifier.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
    }

    private void validatePassword(User user, String rawPassword, LoginRequest request) {
        if (!hashingService.verifyPassword(rawPassword, user.getHashedPassword())) {
            securityProtectionService.recordFailedAuthentication(
                    user,
                    request.getSourceIp(),
                    request.getSourceUserAgent(),
                    request.getDeviceId());
            throw new IllegalArgumentException("Invalid credentials");
        }
    }

    private void validateUserStatus(User user) {
        if (user.getAccountStatus() == User.AccountStatus.EXPIRED) {
            throw new IllegalStateException("Pending registration expired. Restart registration");
        }
        if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }

    private void enforcePendingLifecycle(User user, String identifier) {
        if (user.getAccountStatus() != User.AccountStatus.PENDING_VERIFICATION) {
            return;
        }

        Instant deadline = user.getVerificationDeadline();
        if (deadline != null && Instant.now().isAfter(deadline)) {
            throw new IllegalStateException("Pending registration expired. Restart registration");
        }

        boolean emailLogin = identifier != null && identifier.contains("@");
        if (emailLogin && !user.isEmailVerified()) {
            throw new PendingVerificationException("Email verification is required", user.getId());
        }

        if (!emailLogin && !user.isMobileVerified()) {
            throw new PendingVerificationException("Mobile verification is required", user.getId());
        }
    }

    private void enforceChannelVerification(User user, String identifier) {
        boolean emailLogin = identifier != null && identifier.contains("@");
        if (emailLogin && !user.isEmailVerified()) {
            throw new PendingVerificationException("Email verification is required", user.getId());
        }

        if (!emailLogin && !user.isMobileVerified()) {
            throw new PendingVerificationException("Mobile verification is required", user.getId());
        }
    }

    private void validateMfaIfRequired(User user, LoginRequest request) {
        if (!user.isMfaEnabled()) {
            return;
        }
        if (request.getMfaCode() == null || request.getMfaCode().isBlank()) {
            throw new IllegalArgumentException("MFA code is required");
        }
        boolean verified = totpManagementService.verifyActiveCode(user.getId(), request.getMfaCode());
        if (!verified) {
            throw new IllegalArgumentException("Invalid MFA code");
        }
    }

    private Device upsertDevice(User user, LoginRequest request) {
        return deviceRepository.findByUserIdAndDeviceId(user.getId(), request.getDeviceId())
                .map(existing -> {
                    existing.setDeviceType(request.getDeviceType());
                    existing.setRevoked(false);
                    existing.setSuspicious(false);
                    existing.setTrusted(existing.isTrusted());
                    existing.setUser(user);
                    return existing;
                })
                .orElseGet(() -> {
                    Device device = new Device();
                    device.setUser(user);
                    device.setDeviceId(request.getDeviceId());
                    device.setDeviceType(request.getDeviceType());
                    device.setTrusted(false);
                    device.setSuspicious(false);
                    device.setRevoked(false);
                    return device;
                });
    }

    private void auditAuthenticationFailure(User user, LoginRequest request, String reason) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.USER_ACTIVITY);
        audit.setDescription("AUTH_FAILURE|flow=login|reason=" + reason);
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(request.getSourceIp());
        audit.setSourceUserAgent(request.getSourceUserAgent());
        if (user != null) {
            audit.setUser(user);
        }
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);
    }

    private String classifyFailureReason(RuntimeException ex) {
        String message = ex.getMessage();
        if ("Invalid credentials".equals(message)) {
            return "INVALID_CREDENTIALS";
        }
        if ("Account is temporarily locked".equals(message)) {
            return "ACCOUNT_LOCKED";
        }
        if ("Account is not active".equals(message)) {
            return "ACCOUNT_DISABLED";
        }
        if ("Pending registration expired. Restart registration".equals(message)) {
            return "REGISTRATION_EXPIRED";
        }
        if ("MFA code is required".equals(message)) {
            return "MFA_REQUIRED";
        }
        if ("Invalid MFA code".equals(message)) {
            return "MFA_INVALID";
        }
        if ("Email verification is required".equals(message)
                || "Mobile verification is required".equals(message)) {
            return "VERIFICATION_REQUIRED";
        }
        if ("Rate limit exceeded for user".equals(message)) {
            return "RATE_LIMITED";
        }
        return "AUTH_FAILURE";
    }
}
