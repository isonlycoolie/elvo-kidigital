package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.elvo.identity.dto.request.FastLoginGenerateRequest;
import com.elvo.identity.dto.request.FastLoginVerifyRequest;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.SessionManagementService;

@ExtendWith(MockitoExtension.class)
class FastLoginServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private SessionManagementService sessionManagementService;

    @Mock
    private SecurityHashingService hashingService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private FastLoginServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FastLoginServiceImpl(
                userRepository,
                deviceRepository,
                auditRepository,
                sessionManagementService,
                hashingService,
                redisTemplate,
                30,
                "elvo:fast-login:method:");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void generateShouldRejectWhenDevicePolicyIsBiometricOnly() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        setId(user, userId);
        Device device = new Device();
        device.setDeviceId("dev-1");
        device.setDeviceType("ANDROID_BIO");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceRepository.findByUserIdAndDeviceId(userId, "dev-1")).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(valueOperations.get("elvo:fast-login:method:" + userId + ":dev-1")).thenReturn("biometric");

        FastLoginGenerateRequest request = new FastLoginGenerateRequest();
        request.setUserId(userId);
        request.setDeviceId("dev-1");
        request.setDeviceType("ANDROID_BIO");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generateFastLoginPin(request));
        assertEquals("Fast login method policy requires biometric on this device", ex.getMessage());
    }

    @Test
    void verifyShouldRejectWhenMethodSwitchViolatesPolicy() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        setId(user, userId);
        Device device = new Device();
        device.setDeviceId("dev-2");
        device.setDeviceType("ANDROID_BIO");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(deviceRepository.findByUserIdAndDeviceId(userId, "dev-2")).thenReturn(Optional.of(device));
        when(valueOperations.get("elvo:fast-login:method:" + userId + ":dev-2")).thenReturn("pin");

        FastLoginVerifyRequest request = new FastLoginVerifyRequest();
        request.setUserId(userId);
        request.setDeviceId("dev-2");
        request.setBiometricToken("BIO:" + userId + ":dev-2");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.verifyFastLogin(request));
        assertEquals("Fast login method policy violation for device", ex.getMessage());
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
