package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.EmailRegistrationRequest;
import com.elvo.identity.dto.response.RegistrationResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.messaging.account.AccountCreationIntentPublisher;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;

@ExtendWith(MockitoExtension.class)
class RegistrationLifecycleUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private SecurityHashingService hashingService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private AccountCreationIntentPublisher accountCreationIntentPublisher;

    private RegistrationServiceImpl createRegistrationService() {
        return new RegistrationServiceImpl(
                userRepository,
                auditRepository,
                hashingService,
                auditEventPublisher,
                accountCreationIntentPublisher,
                24L);
    }

    @Test
    void registrationShouldRemainPendingBeforeVerification() {
        AtomicReference<User> savedRef = new AtomicReference<>();

        EmailRegistrationRequest request = new EmailRegistrationRequest();
        request.setEmail("user@elvo.com");
        request.setPassword("Password123");
        request.setEnableMfa(true);

        when(userRepository.findByEmailIgnoreCase("user@elvo.com")).thenReturn(Optional.empty());
        when(hashingService.hashPassword("Password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            savedRef.set(user);
            setId(user, UUID.randomUUID());
            return user;
        });
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = createRegistrationService().registerEmail(request);

        assertNotNull(response.userId());
        assertNull(response.ean());
        User saved = savedRef.get();
        assertNotNull(saved);
        assertEquals(User.AccountStatus.PENDING_VERIFICATION, saved.getAccountStatus());
        assertFalse(saved.isEmailVerified());
        assertFalse(saved.isMobileVerified());
        assertFalse(saved.isDownstreamProvisioned());
        assertNotNull(saved.getVerificationDeadline());
        verify(accountCreationIntentPublisher).publish(any(User.class), any(), any());
    }

    @Test
    void reregistrationShouldReuseExistingPendingAccount() {
        UUID existingId = UUID.randomUUID();
        User existing = new User();
        setId(existing, existingId);
        existing.setAccountStatus(User.AccountStatus.PENDING_VERIFICATION);
        existing.setVerificationDeadline(Instant.now().plusSeconds(3600));

        EmailRegistrationRequest request = new EmailRegistrationRequest();
        request.setEmail("pending@elvo.com");
        request.setPassword("Password123");

        when(userRepository.findByEmailIgnoreCase("pending@elvo.com")).thenReturn(Optional.of(existing));
        when(hashingService.hashPassword("Password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = createRegistrationService().registerEmail(request);

        assertEquals(existingId, response.userId());
        assertEquals(User.AccountStatus.PENDING_VERIFICATION, existing.getAccountStatus());
        assertFalse(existing.isDownstreamProvisioned());
    }

    @Test
    void expiredPendingAccountShouldRequireRestart() {
        User existing = new User();
        setId(existing, UUID.randomUUID());
        existing.setAccountStatus(User.AccountStatus.PENDING_VERIFICATION);
        existing.setVerificationDeadline(Instant.now().minusSeconds(30));

        EmailRegistrationRequest request = new EmailRegistrationRequest();
        request.setEmail("expired@elvo.com");
        request.setPassword("Password123");

        when(userRepository.findByEmailIgnoreCase("expired@elvo.com")).thenReturn(Optional.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> createRegistrationService().registerEmail(request));
        assertEquals("Pending registration expired. Restart registration", ex.getMessage());
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
