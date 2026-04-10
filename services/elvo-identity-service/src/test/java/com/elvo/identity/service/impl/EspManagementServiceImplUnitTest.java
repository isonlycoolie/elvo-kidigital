package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.EspGenerateRequest;
import com.elvo.identity.dto.request.EspVerifyRequest;
import com.elvo.identity.dto.response.EspGenerateResponse;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;

@ExtendWith(MockitoExtension.class)
class EspManagementServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private SecurityHashingService hashingService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private EspManagementServiceImpl service;

    @Test
    void generateEspShouldStoreHashedCodeAndExpiry() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hashingService.hashEsp(any())).thenReturn("hashed-esp");

        EspGenerateRequest request = new EspGenerateRequest();
        request.setUserId(userId);

        EspGenerateResponse response = service.generateEsp(request);

        assertEquals("hashed-esp", user.getEspHash());
        assertTrue(response.expiresAt().isAfter(Instant.now()));
        verify(userRepository).save(user);
    }

    @Test
    void verifyEspShouldReturnTrueWhenCodeMatches() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setEspHash("stored-hash");
        user.setEspExpiresAt(Instant.now().plusSeconds(60));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hashingService.verifyEsp("123456", "stored-hash")).thenReturn(true);

        EspVerifyRequest request = new EspVerifyRequest();
        request.setUserId(userId);
        request.setEspCode("123456");

        assertTrue(service.verifyEsp(request));
        assertTrue(user.isEspEnabled());
        assertEquals(0, user.getEspFailedAttempts());
    }

    @Test
    void verifyEspShouldIncrementFailedAttemptsWhenCodeMismatches() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setEspHash("stored-hash");
        user.setEspExpiresAt(Instant.now().plusSeconds(60));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hashingService.verifyEsp("000000", "stored-hash")).thenReturn(false);

        EspVerifyRequest request = new EspVerifyRequest();
        request.setUserId(userId);
        request.setEspCode("000000");

        assertFalse(service.verifyEsp(request));
        assertEquals(1, user.getEspFailedAttempts());
    }

    @Test
    void verifyEspShouldBlockDuringProgressiveCooldown() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setEspHash("stored-hash");
        user.setEspExpiresAt(Instant.now().plusSeconds(60));
        user.setEspFailedAttempts(2);
        user.setEspLastRequestedAt(Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        EspVerifyRequest request = new EspVerifyRequest();
        request.setUserId(userId);
        request.setEspCode("123456");

        assertFalse(service.verifyEsp(request));
        verify(userRepository).findById(userId);
    }
}
