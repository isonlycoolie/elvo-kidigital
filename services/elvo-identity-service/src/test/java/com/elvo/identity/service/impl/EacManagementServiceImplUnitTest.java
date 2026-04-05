package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.dto.request.EacGenerateRequest;
import com.elvo.identity.dto.response.EacGenerateResponse;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;

@ExtendWith(MockitoExtension.class)
class EacManagementServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private SecurityHashingService hashingService;

    @InjectMocks
    private EacManagementServiceImpl service;

    @Test
    void generateEacShouldReturnCodeAndPersistHash() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        User user = new User();
        setId(user, userId);
        user.setEspEnabled(true);
        user.setAccountStatus(User.AccountStatus.ACTIVE);

        Device device = new Device();
        setId(device, deviceId);
        device.setTrusted(true);
        device.setRevoked(false);
        device.setSuspicious(false);
        device.setUser(user);

        Session session = new Session();
        setId(session, sessionId);
        session.setUser(user);
        session.setDevice(device);
        session.setActive(true);
        session.setRevoked(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(hashingService.hashOneTimeCode(any())).thenReturn("hashed-eac");

        EacGenerateRequest request = new EacGenerateRequest();
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setDeviceId(deviceId);
        request.setAction("TRANSFER");

        EacGenerateResponse response = service.generateEac(request);

        assertNotNull(response.eacCode());
        assertEquals("hashed-eac", user.getEacHash());
        assertTrue(user.getEacExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void generateEacShouldRejectSuspiciousDevice() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        User user = new User();
        setId(user, userId);
        user.setEspEnabled(true);
        user.setAccountStatus(User.AccountStatus.ACTIVE);

        Device device = new Device();
        setId(device, deviceId);
        device.setTrusted(true);
        device.setRevoked(false);
        device.setSuspicious(true);
        device.setUser(user);

        Session session = new Session();
        setId(session, sessionId);
        session.setUser(user);
        session.setDevice(device);
        session.setActive(true);
        session.setRevoked(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        EacGenerateRequest request = new EacGenerateRequest();
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setDeviceId(deviceId);
        request.setAction("TRANSFER");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generateEac(request));
        assertEquals("Device trust verification failed", ex.getMessage());
    }

    private void setId(Object target, UUID id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to set test id", ex);
        }
    }
}
