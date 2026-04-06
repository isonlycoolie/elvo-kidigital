package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.SecurityProtectionService;
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

    private LoginServiceImpl loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginServiceImpl(
                userRepository,
                deviceRepository,
                sessionRepository,
                auditRepository,
                tokenService,
                securityProtectionService,
                hashingService,
                auditEventPublisher);
    }

    @Test
    void disabledUserShouldEmitStructuredAuthFailureAudit() {
        User user = new User();
        user.setEan("ELVO-LOGIN-UNIT-1");
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

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> loginService.login(request));
        assertEquals("Account is not active", ex.getMessage());

        verify(auditRepository).save(argThat(audit ->
            audit.getDescription() != null
                && audit.getDescription().startsWith("AUTH_FAILURE|flow=login|reason=ACCOUNT_DISABLED")));
        verify(auditEventPublisher).publish(any(Audit.class));
    }
}
