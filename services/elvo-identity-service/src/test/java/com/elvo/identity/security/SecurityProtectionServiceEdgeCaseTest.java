package com.elvo.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.impl.SecurityProtectionServiceImpl;

@ExtendWith(MockitoExtension.class)
class SecurityProtectionServiceEdgeCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AuditRepository auditRepository;

    @InjectMocks
    private SecurityProtectionServiceImpl service;

    @Test
    void bruteForceAttemptsShouldLockAccountAndFlagSuspiciousDevice() {
        User user = new User();
        UUID userId = UUID.randomUUID();
        setId(user, userId);
        user.setFailedLoginAttempts(4);
        user.setSuspiciousActivityCount(0);

        Device device = new Device();
        device.setSuspicious(false);
        when(deviceRepository.findByUserIdAndDeviceId(userId, "device-attack-1")).thenReturn(Optional.of(device));

        service.recordFailedAuthentication(user, "127.0.0.1", "edge-test", "device-attack-1");

        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(1, user.getSuspiciousActivityCount());
        assertEquals(true, device.isSuspicious());
        verify(userRepository).save(user);
        verify(deviceRepository, atLeastOnce()).save(device);
    }

    private static void setId(User user, UUID userId) {
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set test user id", ex);
        }
    }

    @Test
    void enforcePerUserRateLimitShouldRejectRapidRequests() {
        User user = new User();
        user.setSecurityLastEventAt(Instant.now());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.enforcePerUserRateLimit(user));
        assertEquals("Rate limit exceeded for user", ex.getMessage());
    }

    @Test
    void ensureAccountNotLockedShouldRejectWhenLockoutActive() {
        User user = new User();
        user.setLockoutUntil(Instant.now().plusSeconds(120));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.ensureAccountNotLocked(user));
        assertEquals("Account is temporarily locked", ex.getMessage());
    }
}
