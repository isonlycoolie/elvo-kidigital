package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.exception.PendingVerificationException;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.IdentityAccountReadService;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.RecoveryCodeService;
import com.elvo.identity.service.RiskScoringService;
import com.elvo.identity.service.SecurityProtectionService;
import com.elvo.identity.service.TotpManagementService;
import com.elvo.identity.contract.RiskDecisionContract;
import com.elvo.identity.util.TokenService;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private SecurityProtectionService securityProtectionService;

    @Mock
    private SecurityHashingService hashingService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private IdentityAccountReadService accountReadService;

    @Mock
    private TotpManagementService totpManagementService;

    @Mock
    private RecoveryCodeService recoveryCodeService;

    @Mock
    private RiskScoringService riskScoringService;

    private LoginServiceImpl createLoginService() {
        return new LoginServiceImpl(
                userRepository,
                deviceRepository,
                sessionRepository,
                auditRepository,
                tokenService,
                securityProtectionService,
                hashingService,
                auditEventPublisher,
                accountReadService,
                totpManagementService,
                recoveryCodeService,
                riskScoringService);
    }

    @Test
    void disabledUserShouldEmitStructuredAuthFailureAudit() {
        User user = new User();
        user.setEmail("disabled@elvo.com");
        user.setPhone("+12025550000");
        user.setHashedPassword("hashed-password");
        user.setAccountStatus(User.AccountStatus.LOCKED);

        LoginRequest request = new LoginRequest();
        request.setIdentifier("disabled@elvo.com");
        request.setPassword("Password123");
        request.setDeviceId("device-1");
        request.setDeviceType("ANDROID");
        request.setSourceIp("127.0.0.1");
        request.setSourceUserAgent("JUnit");

        when(userRepository.findByEmailIgnoreCase("disabled@elvo.com")).thenReturn(Optional.of(user));
        when(hashingService.verifyPassword("Password123", "hashed-password")).thenReturn(true);
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> createLoginService().login(request));
        assertEquals("Account is not active", ex.getMessage());

        verify(auditRepository).save(argThat(audit ->
            audit.getDescription() != null
                && audit.getDescription().startsWith("AUTH_FAILURE|flow=login|reason=ACCOUNT_DISABLED")));
        verify(auditEventPublisher).publish(any(Audit.class));
    }

    @Test
    void pendingUserLoginShouldReturnVerificationRequired() {
        User user = new User();
        user.setEmail("pending@elvo.com");
        user.setHashedPassword("hashed-password");
        user.setAccountStatus(User.AccountStatus.PENDING_VERIFICATION);
        user.setVerificationDeadline(java.time.Instant.now().plusSeconds(600));
        user.setEmailVerified(false);

        LoginRequest request = new LoginRequest();
        request.setIdentifier("pending@elvo.com");
        request.setPassword("Password123");
        request.setDeviceId("device-1");
        request.setDeviceType("ANDROID");

        when(userRepository.findByEmailIgnoreCase("pending@elvo.com")).thenReturn(Optional.of(user));
        when(hashingService.verifyPassword("Password123", "hashed-password")).thenReturn(true);
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PendingVerificationException ex = assertThrows(PendingVerificationException.class, () -> createLoginService().login(request));
        assertEquals("Email verification is required", ex.getMessage());
        verify(sessionRepository, never()).save(any());
        verify(tokenService, never()).generateAccessToken(any(), any());
    }

    @Test
    void expiredPendingLoginShouldRequireRestart() {
        User user = new User();
        user.setEmail("expired@elvo.com");
        user.setHashedPassword("hashed-password");
        user.setAccountStatus(User.AccountStatus.PENDING_VERIFICATION);
        user.setVerificationDeadline(java.time.Instant.now().minusSeconds(5));

        LoginRequest request = new LoginRequest();
        request.setIdentifier("expired@elvo.com");
        request.setPassword("Password123");
        request.setDeviceId("device-1");
        request.setDeviceType("ANDROID");

        when(userRepository.findByEmailIgnoreCase("expired@elvo.com")).thenReturn(Optional.of(user));
        when(hashingService.verifyPassword("Password123", "hashed-password")).thenReturn(true);
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> createLoginService().login(request));
        assertEquals("Pending registration expired. Restart registration", ex.getMessage());
    }

    @Test
    void mfaEnabledUserShouldRejectInvalidTotpCode() {
        User user = new User();
        setId(user, UUID.randomUUID());
        user.setEmail("mfa@elvo.com");
        user.setHashedPassword("hashed-password");
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setMfaEnabled(true);

        LoginRequest request = new LoginRequest();
        request.setIdentifier("mfa@elvo.com");
        request.setPassword("Password123");
        request.setMfaCode("000000");
        request.setDeviceId("device-1");
        request.setDeviceType("ANDROID");

        when(userRepository.findByEmailIgnoreCase("mfa@elvo.com")).thenReturn(Optional.of(user));
        when(hashingService.verifyPassword("Password123", "hashed-password")).thenReturn(true);
        when(riskScoringService.evaluateLogin(any(), any(), any(Boolean.class))).thenReturn(RiskDecisionContract.allow(10, java.util.List.of()));
        when(totpManagementService.verifyActiveCode(user.getId(), "000000")).thenReturn(false);
        when(recoveryCodeService.consumeCode(user.getId(), "000000")).thenReturn(false);
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> createLoginService().login(request));
        assertEquals("Invalid MFA code", ex.getMessage());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void mfaEnabledUserShouldAllowBackupRecoveryCode() {
        User user = new User();
        setId(user, UUID.randomUUID());
        user.setEmail("backup@elvo.com");
        user.setHashedPassword("hashed-password");
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setMfaEnabled(true);

        LoginRequest request = new LoginRequest();
        request.setIdentifier("backup@elvo.com");
        request.setPassword("Password123");
        request.setMfaCode("ABCDEF1234");
        request.setDeviceId("device-1");
        request.setDeviceType("ANDROID");

        when(userRepository.findByEmailIgnoreCase("backup@elvo.com")).thenReturn(Optional.of(user));
        when(hashingService.verifyPassword("Password123", "hashed-password")).thenReturn(true);
        when(riskScoringService.evaluateLogin(any(), any(), any(Boolean.class))).thenReturn(RiskDecisionContract.allow(10, java.util.List.of()));
        when(totpManagementService.verifyActiveCode(user.getId(), "ABCDEF1234")).thenReturn(false);
        when(recoveryCodeService.consumeCode(user.getId(), "ABCDEF1234")).thenReturn(true);
        when(accountReadService.resolveEan(user.getId())).thenThrow(new IllegalStateException("Fallback account service is unavailable"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> createLoginService().login(request));
        assertEquals("Fallback account service is unavailable", ex.getMessage());
    }

    private void setId(User user, UUID id) {
        try {
            java.lang.reflect.Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to set test id", ex);
        }
    }
}
