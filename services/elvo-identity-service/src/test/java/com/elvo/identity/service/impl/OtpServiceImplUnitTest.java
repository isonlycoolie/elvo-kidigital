package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.entity.VerificationOtp;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.VerificationOtpRepository;
import com.elvo.identity.security.OtpCryptoService;
import com.elvo.identity.service.EmailSenderService;
import com.elvo.identity.service.OtpRateLimitService;
import com.elvo.identity.service.SmsSenderService;
import com.elvo.identity.service.VerificationPolicyService;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplUnitTest {

    @Mock
    private VerificationOtpRepository verificationOtpRepository;

    @Mock
    private VerificationPolicyService verificationPolicyService;

    @Mock
    private OtpRateLimitService otpRateLimitService;

    @Mock
    private OtpCryptoService otpCryptoService;

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private SmsSenderService smsSenderService;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @InjectMocks
    private OtpServiceImpl otpService;

    @Test
    void issueVerificationOtpShouldDispatchEmailAndSaveHash() {
        User user = user();
        when(verificationPolicyService.otpTtl()).thenReturn(Duration.ofMinutes(5));
        when(verificationOtpRepository.lockActiveOtps(eq(user.getId()), eq(VerificationOtp.Purpose.EMAIL_VERIFICATION))).thenReturn(List.of());
        when(otpCryptoService.generateSixDigitCode()).thenReturn("123456");
        when(otpCryptoService.hashOtp(eq(user.getId()), eq(VerificationOtp.Purpose.EMAIL_VERIFICATION), any(), eq("123456"), any())).thenReturn("hash");
        when(verificationOtpRepository.save(any(VerificationOtp.class))).thenAnswer(invocation -> {
            VerificationOtp otp = invocation.getArgument(0);
            setId(otp, UUID.randomUUID());
            return otp;
        });
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtpServiceImpl.OtpDispatchResult result = otpService.issueVerificationOtp(
                user,
                VerificationOtp.Channel.EMAIL,
                VerificationOtp.Purpose.EMAIL_VERIFICATION,
                "user@example.com",
                false,
                null,
                null,
                "10.1.1.1",
                "device-1");

        assertTrue(result.requestId() != null && !result.requestId().isBlank());
        verify(emailSenderService).sendVerificationOtp(eq("user@example.com"), eq("123456"), eq(Duration.ofMinutes(5)), eq(result.requestId()));
        verify(smsSenderService, never()).sendVerificationOtp(any(), any(), any(), any());
    }

    @Test
    void verifyOtpShouldMarkUsedOnSuccess() {
        User user = user();
        VerificationOtp otp = activeOtp(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION);
        when(verificationOtpRepository.lockByRequestId(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION, "req-1"))
                .thenReturn(Optional.of(otp));
        when(otpCryptoService.matches(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION, otp.getDestination(), otp.getRequestId(), "123456", otp.getOtpHash()))
                .thenReturn(true);
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtpServiceImpl.OtpVerificationResult result = otpService.verifyOtp(
                user,
                VerificationOtp.Purpose.EMAIL_VERIFICATION,
                "123456",
                "req-1",
                "10.1.1.1",
                "device-1");

        assertTrue(result.success());
        assertEquals(VerificationOtp.Status.USED, otp.getStatus());
        assertTrue(otp.getConsumedAt() != null);
    }

    @Test
    void verifyOtpShouldIncrementAttemptsWhenInvalid() {
        User user = user();
        VerificationOtp otp = activeOtp(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION);
        when(verificationOtpRepository.lockByRequestId(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION, "req-1"))
                .thenReturn(Optional.of(otp));
        when(verificationPolicyService.maxOtpAttempts()).thenReturn(5);
        when(otpCryptoService.matches(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION, otp.getDestination(), otp.getRequestId(), "999999", otp.getOtpHash()))
                .thenReturn(false);
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtpServiceImpl.OtpVerificationResult result = otpService.verifyOtp(
                user,
                VerificationOtp.Purpose.EMAIL_VERIFICATION,
                "999999",
                "req-1",
                "10.1.1.1",
                "device-1");

        assertFalse(result.success());
        assertEquals(1, otp.getAttemptCount());
        assertEquals(VerificationOtp.Status.ACTIVE, otp.getStatus());
    }

    @Test
    void verifyOtpShouldRejectExpiredCode() {
        User user = user();
        VerificationOtp otp = activeOtp(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION);
        otp.setExpiresAt(Instant.now().minusSeconds(10));
        when(verificationOtpRepository.lockByRequestId(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION, "req-1"))
                .thenReturn(Optional.of(otp));
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtpServiceImpl.OtpVerificationResult result = otpService.verifyOtp(
                user,
                VerificationOtp.Purpose.EMAIL_VERIFICATION,
                "123456",
                "req-1",
                "10.1.1.1",
                "device-1");

        assertFalse(result.success());
        assertEquals("OTP_EXPIRED", result.code());
        assertEquals(VerificationOtp.Status.EXPIRED, otp.getStatus());
    }

    @Test
    void verifyOtpShouldLockAfterMaxAttempts() {
        User user = user();
        VerificationOtp otp = activeOtp(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION);
        otp.setAttemptCount(4);
        when(verificationOtpRepository.lockByRequestId(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION, "req-1"))
                .thenReturn(Optional.of(otp));
        when(verificationPolicyService.maxOtpAttempts()).thenReturn(5);
        when(otpCryptoService.matches(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION, otp.getDestination(), otp.getRequestId(), "000000", otp.getOtpHash()))
                .thenReturn(false);
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OtpServiceImpl.OtpVerificationResult result = otpService.verifyOtp(
                user,
                VerificationOtp.Purpose.EMAIL_VERIFICATION,
                "000000",
                "req-1",
                "10.1.1.1",
                "device-1");

        assertFalse(result.success());
        assertEquals("OTP_LOCKED", result.code());
        assertEquals(VerificationOtp.Status.LOCKED, otp.getStatus());
    }

    @Test
    void resendShouldEnforceCooldown() {
        User user = user();
        VerificationOtp activeOtp = activeOtp(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION);
        setField(activeOtp, "createdAt", Instant.now());
        when(verificationOtpRepository.countByUserAndPurposeSince(any(), any(), any())).thenReturn(1L);
        when(verificationOtpRepository.lockActiveOtps(eq(user.getId()), eq(VerificationOtp.Purpose.EMAIL_VERIFICATION))).thenReturn(List.of(activeOtp));
        when(verificationPolicyService.resendWindow()).thenReturn(Duration.ofMinutes(15));
        when(verificationPolicyService.maxResendsPerWindow()).thenReturn(3);
        when(verificationPolicyService.resendCooldown()).thenReturn(Duration.ofSeconds(60));
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> otpService.issueVerificationOtp(
                user,
                VerificationOtp.Channel.EMAIL,
                VerificationOtp.Purpose.EMAIL_VERIFICATION,
                "user@example.com",
                true,
                null,
                null,
                "10.1.1.1",
                "device-1"));

        assertEquals("OTP resend cooldown active", ex.getMessage());
    }

    @Test
    void resendShouldEnforceWindowLimit() {
        User user = user();
        when(verificationOtpRepository.countByUserAndPurposeSince(any(), any(), any())).thenReturn(4L);
        when(verificationPolicyService.resendWindow()).thenReturn(Duration.ofMinutes(15));
        when(verificationPolicyService.maxResendsPerWindow()).thenReturn(3);
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> otpService.issueVerificationOtp(
                user,
                VerificationOtp.Channel.EMAIL,
                VerificationOtp.Purpose.EMAIL_VERIFICATION,
                "user@example.com",
                true,
                null,
                null,
                "10.1.1.1",
                "device-1"));

        assertEquals("OTP resend limit reached", ex.getMessage());
    }

    @Test
    void verifyShouldCallRateLimiter() {
        User user = user();
        when(verificationOtpRepository.lockByRequestId(user.getId(), VerificationOtp.Purpose.EMAIL_VERIFICATION, "req-1"))
                .thenReturn(Optional.empty());
        when(auditRepository.save(any(Audit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        otpService.verifyOtp(user, VerificationOtp.Purpose.EMAIL_VERIFICATION, "123456", "req-1", "10.1.1.1", "device-1");

        verify(otpRateLimitService).enforceVerifyLimit(VerificationOtp.Purpose.EMAIL_VERIFICATION, "10.1.1.1", "device-1");
    }

    private User user() {
        User user = new User();
        setId(user, UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPhone("+2349001234567");
        return user;
    }

    private VerificationOtp activeOtp(UUID userId, VerificationOtp.Purpose purpose) {
        VerificationOtp otp = new VerificationOtp();
        setId(otp, UUID.randomUUID());
        otp.setUserId(userId);
        otp.setChannel(VerificationOtp.Channel.EMAIL);
        otp.setPurpose(purpose);
        otp.setDestination("user@example.com");
        otp.setRequestId("req-1");
        otp.setOtpHash("hash");
        otp.setStatus(VerificationOtp.Status.ACTIVE);
        otp.setExpiresAt(Instant.now().plusSeconds(300));
        setField(otp, "createdAt", Instant.now().minusSeconds(120));
        return otp;
    }

    private void setId(Object target, UUID id) {
        setField(target, "id", id);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to set test field", ex);
        }
    }
}
