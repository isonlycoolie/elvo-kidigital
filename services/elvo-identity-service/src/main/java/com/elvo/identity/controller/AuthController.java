package com.elvo.identity.controller;

import java.time.Duration;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.AuthLogoutAllRequest;
import com.elvo.identity.dto.request.AuthLogoutRequest;
import com.elvo.identity.dto.request.AuthRefreshTokenRequest;
import com.elvo.identity.dto.request.ForgotPasswordRequest;
import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.dto.request.RegistrationRequest;
import com.elvo.identity.dto.request.ResetPasswordRequest;
import com.elvo.identity.dto.response.AuthActionResponse;
import com.elvo.identity.dto.response.ForgotPasswordResponse;
import com.elvo.identity.dto.response.LoginResponse;
import com.elvo.identity.dto.response.RegistrationResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.exception.ApiResponse;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.security.TokenRevocationService;
import com.elvo.identity.service.LoginService;
import com.elvo.identity.service.RegistrationService;
import com.elvo.identity.util.TokenService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(15);

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final TokenService tokenService;
    private final TokenRevocationService tokenRevocationService;
    private final SecurityHashingService hashingService;

    public AuthController(RegistrationService registrationService,
                          LoginService loginService,
                          SessionRepository sessionRepository,
                          UserRepository userRepository,
                          AuditRepository auditRepository,
                          AuditEventPublisher auditEventPublisher,
                          TokenService tokenService,
                          TokenRevocationService tokenRevocationService,
                          SecurityHashingService hashingService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.tokenService = tokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.hashingService = hashingService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(@Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = registrationService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody AuthRefreshTokenRequest request) {
        try {
            TokenService.RefreshTokenClaims claims = tokenService.validateRefreshToken(request.getRefreshToken());
            Session session = sessionRepository.findByRefreshToken(request.getRefreshToken())
                    .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid"));

            if (!session.getUser().getId().equals(claims.userId())) {
                throw new IllegalArgumentException("Refresh token is invalid");
            }

            if (session.isRevoked() || !session.isActive() || Instant.now().isAfter(session.getExpiresAt())) {
                throw new IllegalStateException("Session is expired or revoked");
            }

            tokenRevocationService.revokeJti(claims.jti(), claims.expiresAt());
            revokeSessionAccessTokenIfPresent(session);

            TokenService.TokenPayload accessToken = tokenService.generateAccessToken(session.getUser().getId(), session.getUser().getEan());
            TokenService.TokenPayload refreshToken = tokenService.generateRefreshToken(session.getUser().getId());
            session.setJwtToken(accessToken.token());
            session.setRefreshToken(refreshToken.token());
            session.setExpiresAt(refreshToken.expiresAt());
            sessionRepository.save(session);

            Audit audit = new Audit();
            audit.setActionType(Audit.ActionType.USER_ACTIVITY);
            audit.setDescription("Refresh token issued");
            audit.setSourceType(Audit.SourceType.API);
            audit.setSourceIp(request.getSourceIp());
            audit.setSourceUserAgent(request.getSourceUserAgent());
            audit.setSessionId(session.getId());
            audit.setUser(session.getUser());
            Audit savedAudit = auditRepository.save(audit);
            auditEventPublisher.publish(savedAudit);

            LoginResponse response = new LoginResponse(
                    session.getUser().getId(),
                    accessToken.token(),
                    refreshToken.token(),
                    accessToken.expiresAt(),
                    refreshToken.expiresAt(),
                    session.getId());
            return ResponseEntity.ok(ApiResponse.ok("Token refreshed", response));
        } catch (RuntimeException ex) {
            auditAuthFailure(
                    "refresh-token",
                    classifyAuthFailureReason(ex),
                    request.getSourceIp(),
                    request.getSourceUserAgent(),
                    null,
                    null);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<AuthActionResponse>> logout(@Valid @RequestBody AuthLogoutRequest request) {
        try {
            TokenService.RefreshTokenClaims claims = tokenService.validateRefreshToken(request.getRefreshToken());
            int updated = sessionRepository.findByRefreshToken(request.getRefreshToken())
                    .map(session -> {
                        if (!session.getUser().getId().equals(claims.userId())) {
                            throw new IllegalArgumentException("Refresh token is invalid");
                        }
                        tokenRevocationService.revokeJti(claims.jti(), claims.expiresAt());
                        revokeSessionAccessTokenIfPresent(session);
                        auditSessionEvent(session, "Logout completed", request.getSourceIp(), request.getSourceUserAgent());
                        return sessionRepository.revokeSession(session.getId());
                    })
                    .orElse(0);

            return ResponseEntity.ok(ApiResponse.ok("Logout processed", new AuthActionResponse(updated > 0, "Logout complete")));
        } catch (RuntimeException ex) {
            auditAuthFailure(
                    "logout",
                    classifyAuthFailureReason(ex),
                    request.getSourceIp(),
                    request.getSourceUserAgent(),
                    null,
                    null);
            throw ex;
        }
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<AuthActionResponse>> logoutAll(@Valid @RequestBody AuthLogoutAllRequest request) {
        sessionRepository.findByUserIdAndActiveTrueAndRevokedFalseOrderByCreatedAtDesc(request.getUserId())
                .forEach(session -> {
                    try {
                        TokenService.RefreshTokenClaims refreshClaims = tokenService.validateRefreshToken(session.getRefreshToken());
                        tokenRevocationService.revokeJti(refreshClaims.jti(), refreshClaims.expiresAt());
                    } catch (IllegalArgumentException ex) {
                        // Skip already-invalid refresh token entries.
                    }
                    revokeSessionAccessTokenIfPresent(session);
                });

        int updated = sessionRepository.revokeActiveSessionsByUserId(request.getUserId());
        if (updated > 0) {
            Audit audit = new Audit();
            audit.setActionType(Audit.ActionType.SESSION_REVOCATION);
            audit.setDescription("Logout-all completed");
            audit.setSourceType(Audit.SourceType.API);
            audit.setSourceIp(request.getSourceIp());
            audit.setSourceUserAgent(request.getSourceUserAgent());
            userRepository.findById(request.getUserId()).ifPresent(audit::setUser);
            Audit savedAudit = auditRepository.save(audit);
            auditEventPublisher.publish(savedAudit);
        }
        return ResponseEntity.ok(ApiResponse.ok("Logout-all processed", new AuthActionResponse(updated > 0, "All sessions revoked")));
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getIdentifier().toLowerCase(Locale.ROOT))
                .or(() -> userRepository.findByPhone(request.getIdentifier().trim()))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String resetToken = generateResetToken();
        user.setPasswordResetHash(hashingService.hashOneTimeCode(resetToken));
        user.setPasswordResetExpiresAt(Instant.now().plus(PASSWORD_RESET_TTL));
        user.setPasswordResetFailedAttempts(0);
        user.setPasswordResetLastRequestedAt(Instant.now());
        userRepository.save(user);

        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.USER_ACTIVITY);
        audit.setDescription("Password reset requested");
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(request.getSourceIp());
        audit.setSourceUserAgent(request.getSourceUserAgent());
        audit.setUser(user);
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);

        return ResponseEntity.ok(ApiResponse.ok("Password reset token generated", new ForgotPasswordResponse(user.getId(), resetToken, user.getPasswordResetExpiresAt())));
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<ApiResponse<AuthActionResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getPasswordResetHash() == null || user.getPasswordResetExpiresAt() == null || Instant.now().isAfter(user.getPasswordResetExpiresAt())) {
            throw new IllegalStateException("Password reset token is expired");
        }

        if (!hashingService.verifyOneTimeCode(request.getResetToken(), user.getPasswordResetHash())) {
            user.setPasswordResetFailedAttempts(user.getPasswordResetFailedAttempts() + 1);
            userRepository.save(user);
            throw new IllegalArgumentException("Password reset token is invalid");
        }

        user.setHashedPassword(hashingService.hashPassword(request.getNewPassword()));
        user.setPasswordResetHash(null);
        user.setPasswordResetExpiresAt(null);
        user.setPasswordResetFailedAttempts(0);
        user.setPasswordResetLastRequestedAt(null);
        userRepository.save(user);

        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.PASSWORD_CHANGE);
        audit.setDescription("Password reset completed");
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(request.getSourceIp());
        audit.setSourceUserAgent(request.getSourceUserAgent());
        audit.setUser(user);
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);

        return ResponseEntity.ok(ApiResponse.ok("Password updated", new AuthActionResponse(true, "Password reset complete")));
    }

    private String generateResetToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void auditSessionEvent(Session session, String description, String sourceIp, String sourceUserAgent) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.SESSION_REVOCATION);
        audit.setDescription(description);
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        audit.setSessionId(session.getId());
        audit.setDeviceId(session.getDevice().getId());
        audit.setUser(session.getUser());
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);
    }

    private void revokeSessionAccessTokenIfPresent(Session session) {
        if (session.getJwtToken() == null || session.getJwtToken().isBlank()) {
            return;
        }
        try {
            TokenService.AccessTokenClaims accessClaims = tokenService.validateAccessToken(session.getJwtToken());
            tokenRevocationService.revokeJti(accessClaims.jti(), accessClaims.expiresAt());
        } catch (IllegalArgumentException ex) {
            // Access token may already be invalid/expired and does not require denylist storage.
        }
    }

    private void auditAuthFailure(String flow,
                                  String reason,
                                  String sourceIp,
                                  String sourceUserAgent,
                                  Session session,
                                  User user) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.USER_ACTIVITY);
        audit.setDescription("AUTH_FAILURE|flow=" + flow + "|reason=" + reason);
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        if (session != null) {
            audit.setSessionId(session.getId());
        }
        if (user != null) {
            audit.setUser(user);
        }
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);
    }

    private String classifyAuthFailureReason(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return "AUTH_FAILURE";
        }
        if ("Token is invalid".equals(message)) {
            return "TOKEN_INVALID";
        }
        if ("Token issuer is invalid".equals(message) || "Token audience is invalid".equals(message)) {
            return "TOKEN_CLAIMS_INVALID";
        }
        if ("Token is revoked".equals(message)) {
            return "TOKEN_REVOKED";
        }
        if ("Refresh token is invalid".equals(message)) {
            return "REFRESH_TOKEN_INVALID";
        }
        if ("Session is expired or revoked".equals(message)) {
            return "SESSION_STALE";
        }
        return "AUTH_FAILURE";
    }
}
