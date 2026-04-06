package com.elvo.identity.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.User;
import com.elvo.identity.entity.VerificationOtp;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.repository.VerificationOtpRepository;
import com.elvo.identity.service.VerificationTokenService;

@Component
public class PendingRegistrationCleanupJob {

    private final UserRepository userRepository;
    private final VerificationOtpRepository verificationOtpRepository;
    private final VerificationTokenService verificationTokenService;
    private final Duration expiryThreshold;

    public PendingRegistrationCleanupJob(
            UserRepository userRepository,
            VerificationOtpRepository verificationOtpRepository,
            VerificationTokenService verificationTokenService,
            @Value("${elvo.security.pending-registration.expiry-hours:24}") long expiryHours) {
        this.userRepository = userRepository;
        this.verificationOtpRepository = verificationOtpRepository;
        this.verificationTokenService = verificationTokenService;
        this.expiryThreshold = Duration.ofHours(Math.max(1, expiryHours));
    }

    @Scheduled(fixedDelayString = "${elvo.security.pending-registration.cleanup-interval-ms:3600000}")
    @Transactional
    public void expireStalePendingRegistrations() {
        Instant now = Instant.now();
        Instant legacyCutoff = now.minus(expiryThreshold);

        List<User> staleUsers = userRepository.findByAccountStatusAndVerificationDeadlineBefore(
                User.AccountStatus.PENDING_VERIFICATION,
            now);
        staleUsers.addAll(userRepository.findByAccountStatusAndVerificationDeadlineIsNullAndCreatedAtBefore(
            User.AccountStatus.PENDING_VERIFICATION,
            legacyCutoff));

        for (User user : staleUsers) {
            user.setAccountStatus(User.AccountStatus.EXPIRED);
            user.setVerificationStatus(User.VerificationStatus.UNVERIFIED);
            verificationOtpRepository.invalidateAllActiveOtps(user.getId(), VerificationOtp.Status.REVOKED);
            verificationTokenService.invalidateForUser(user.getId());
        }
    }
}
