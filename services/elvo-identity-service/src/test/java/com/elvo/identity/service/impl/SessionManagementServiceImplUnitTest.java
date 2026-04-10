package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.SessionCreateRequest;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.IdentityAccountReadService;
import com.elvo.identity.util.TokenService;

@ExtendWith(MockitoExtension.class)
class SessionManagementServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant ABSOLUTE_SESSION_EXPIRY = NOW.plusSeconds(30L * 24 * 60 * 60);
    private static final Instant REFRESH_EXPIRY = NOW.plusSeconds(7L * 24 * 60 * 60);

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
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private IdentityAccountReadService accountReadService;

    private SessionManagementServiceImpl sessionManagementService;

    @BeforeEach
    void setUp() {
        sessionManagementService = new SessionManagementServiceImpl(
                userRepository,
                deviceRepository,
                sessionRepository,
                auditRepository,
                tokenService,
                auditEventPublisher,
                accountReadService);
    }

    @Test
    void createSessionShouldPersistAbsoluteExpiryAndCapRefreshToken() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        setId(user, userId);

        Device device = new Device();
        device.setDeviceId("device-1");
        device.setDeviceType("ANDROID");
        device.setTrusted(true);
        device.setRevoked(false);
        device.setSuspicious(false);
        device.setUser(user);

        SessionCreateRequest request = new SessionCreateRequest();
        request.setUserId(userId);
        request.setDeviceId("device-1");
        request.setDeviceType("ANDROID");
        request.setSourceIp("127.0.0.1");
        request.setSourceUserAgent("JUnit");

        TokenService.TokenPayload accessToken = new TokenService.TokenPayload("access.jwt", NOW.plusSeconds(5 * 60L));
        TokenService.TokenPayload refreshToken = new TokenService.TokenPayload("refresh.jwt", REFRESH_EXPIRY);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceRepository.findByUserIdAndDeviceId(userId, "device-1")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountReadService.resolveEan(userId)).thenReturn("ELVO-SESSION-UNIT-1");
        when(tokenService.generateAccessToken(userId, "ELVO-SESSION-UNIT-1")).thenReturn(accessToken);
        when(tokenService.calculateSessionAbsoluteExpiry()).thenReturn(ABSOLUTE_SESSION_EXPIRY);
        when(tokenService.generateRefreshToken(userId, ABSOLUTE_SESSION_EXPIRY)).thenReturn(refreshToken);
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = sessionManagementService.createSession(request);

        assertEquals(accessToken.token(), response.accessToken());
        assertEquals(refreshToken.token(), response.refreshToken());
        assertEquals(REFRESH_EXPIRY, response.refreshExpiresAt());
        verify(sessionRepository).save(argThat(session ->
                ABSOLUTE_SESSION_EXPIRY.equals(session.getAbsoluteExpiresAt())
                        && REFRESH_EXPIRY.equals(session.getExpiresAt())
                        && session.isActive()
                        && !session.isRevoked()));
    }

    private static void setId(User user, UUID userId) {
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set test user id", ex);
        }
    }
}
