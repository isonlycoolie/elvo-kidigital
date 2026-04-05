package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.dto.request.EacVerifyRequest;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;

@ExtendWith(MockitoExtension.class)
class EacManagementSecurityEdgeCaseTest {

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
    void suspiciousDeviceShouldBeBlockedDuringEacVerification() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        User user = new User();
        setId(user, userId);
        user.setEspEnabled(true);
        user.setAccountStatus(User.AccountStatus.ACTIVE);

        Device device = new Device();
        setId(device, deviceId);
        device.setUser(user);
        device.setTrusted(true);
        device.setRevoked(false);
        device.setSuspicious(true);

        Session session = new Session();
        setId(session, sessionId);
        session.setUser(user);
        session.setDevice(device);
        session.setActive(true);
        session.setRevoked(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        EacVerifyRequest request = new EacVerifyRequest();
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setDeviceId(deviceId);
        request.setEacCode("ABCDEFG1");
        request.setAction("TRANSFER");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.verifyEac(request));
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
