package com.elvo.identity.service.impl;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.dto.response.LoginResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.LoginService;
import com.elvo.identity.util.TokenService;

@Service
public class LoginServiceImpl implements LoginService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final SessionRepository sessionRepository;
    private final AuditRepository auditRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginServiceImpl(UserRepository userRepository,
                            DeviceRepository deviceRepository,
                            SessionRepository sessionRepository,
                            AuditRepository auditRepository,
                            TokenService tokenService) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.auditRepository = auditRepository;
        this.tokenService = tokenService;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = loadUser(request.getIdentifier());
        validatePassword(user, request.getPassword());
        validateUserStatus(user);
        validateMfaIfRequired(user, request);

        Device device = upsertDevice(user, request);

        TokenService.TokenPayload accessToken = tokenService.generateAccessToken(user.getId(), user.getEan());
        TokenService.TokenPayload refreshToken = tokenService.generateRefreshToken(user.getId());

        Session session = new Session();
        session.setUser(user);
        session.setDevice(device);
        session.setJwtToken(accessToken.token());
        session.setRefreshToken(refreshToken.token());
        session.setExpiresAt(refreshToken.expiresAt());
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
        auditRepository.save(audit);

        return new LoginResponse(
                user.getId(),
                accessToken.token(),
                refreshToken.token(),
                accessToken.expiresAt(),
                refreshToken.expiresAt(),
                savedSession.getId());
    }

    private User loadUser(String identifier) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmailIgnoreCase(normalized)
                .or(() -> userRepository.findByPhone(identifier.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
    }

    private void validatePassword(User user, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, user.getHashedPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
    }

    private void validateUserStatus(User user) {
        if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }

    private void validateMfaIfRequired(User user, LoginRequest request) {
        if (user.isMfaEnabled() && (request.getMfaCode() == null || request.getMfaCode().isBlank())) {
            throw new IllegalArgumentException("MFA code is required");
        }
    }

    private Device upsertDevice(User user, LoginRequest request) {
        return deviceRepository.findByDeviceId(request.getDeviceId())
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
}
