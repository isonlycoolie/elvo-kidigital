package com.elvo.identity.service.impl;

import java.util.Locale;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.EmailRegistrationRequest;
import com.elvo.identity.dto.request.MobileRegistrationRequest;
import com.elvo.identity.dto.request.RegistrationRequest;
import com.elvo.identity.dto.response.RegistrationResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.messaging.account.AccountCreationIntentPublisher;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.RegistrationService;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final SecurityHashingService hashingService;
    private final AuditEventPublisher auditEventPublisher;
    private final AccountCreationIntentPublisher accountCreationIntentPublisher;
    private final Duration verificationDeadlineDuration;

    public RegistrationServiceImpl(UserRepository userRepository,
                                   AuditRepository auditRepository,
                                   SecurityHashingService hashingService,
                                   AuditEventPublisher auditEventPublisher,
                                   AccountCreationIntentPublisher accountCreationIntentPublisher,
                                   @Value("${elvo.security.pending-registration.expiry-hours:24}") long pendingRegistrationExpiryHours) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.hashingService = hashingService;
        this.auditEventPublisher = auditEventPublisher;
        this.accountCreationIntentPublisher = accountCreationIntentPublisher;
        this.verificationDeadlineDuration = Duration.ofHours(Math.max(1, pendingRegistrationExpiryHours));
    }

    @Override
    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String phone = request.getPhone().trim();

        User user = resolvePendingUser(email, phone)
            .map(existing -> refreshPendingUser(existing, email, phone, request.getPassword(), request.isEnableMfa(), request.getDisplayName()))
            .orElseGet(() -> buildPendingUser(email, phone, request.getPassword(), request.isEnableMfa(), request.getDisplayName()));

        User savedUser = userRepository.save(user);
        auditRegistration(savedUser, request.getSourceIp(), request.getSourceUserAgent());
        accountCreationIntentPublisher.publish(savedUser, request.getSourceIp(), request.getSourceUserAgent());

        return toResponse(savedUser);
    }

    @Override
    @Transactional
    public RegistrationResponse registerEmail(EmailRegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
            .map(existing -> {
                ensurePendingReusable(existing, "Email is already registered");
                return refreshPendingUser(existing, email, existing.getPhone(), request.getPassword(), request.isEnableMfa(), request.getDisplayName());
            })
            .orElseGet(() -> buildPendingUser(email, null, request.getPassword(), request.isEnableMfa(), request.getDisplayName()));

        User savedUser = userRepository.save(user);
        auditRegistration(savedUser, request.getSourceIp(), request.getSourceUserAgent());
        accountCreationIntentPublisher.publish(savedUser, request.getSourceIp(), request.getSourceUserAgent());

        return toResponse(savedUser);
    }

    @Override
    @Transactional
    public RegistrationResponse registerMobile(MobileRegistrationRequest request) {
        String phone = request.getPhone().trim();
        User user = userRepository.findByPhone(phone)
                .map(existing -> {
                    ensurePendingReusable(existing, "Phone is already registered");
                    return refreshPendingUser(existing, existing.getEmail(), phone, request.getPassword(), request.isEnableMfa(), request.getDisplayName());
                })
                .orElseGet(() -> buildPendingUser(null, phone, request.getPassword(), request.isEnableMfa(), request.getDisplayName()));

        User savedUser = userRepository.save(user);
        auditRegistration(savedUser, request.getSourceIp(), request.getSourceUserAgent());
        accountCreationIntentPublisher.publish(savedUser, request.getSourceIp(), request.getSourceUserAgent());

        return toResponse(savedUser);
    }

    private User buildPendingUser(String email,
                                  String phone,
                                  String rawPassword,
                                  boolean mfaEnabled,
                                  String displayName) {
        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setDisplayName(displayName);
        user.setHashedPassword(hashingService.hashPassword(rawPassword));
        user.setMfaEnabled(mfaEnabled);
        user.setEspEnabled(false);
        user.setEmailVerified(false);
        user.setMobileVerified(false);
        user.setVerificationStatus(User.VerificationStatus.UNVERIFIED);
        user.setAccountStatus(User.AccountStatus.PENDING_VERIFICATION);
        user.setVerificationDeadline(Instant.now().plus(verificationDeadlineDuration));
        return user;
    }

    private User refreshPendingUser(User existing,
                                    String email,
                                    String phone,
                                    String rawPassword,
                                    boolean mfaEnabled,
                                    String displayName) {
        existing.setEmail(email);
        existing.setPhone(phone);
        existing.setDisplayName(displayName);
        existing.setHashedPassword(hashingService.hashPassword(rawPassword));
        existing.setMfaEnabled(mfaEnabled);
        existing.setEmailVerified(false);
        existing.setMobileVerified(false);
        existing.setEmailVerifiedAt(null);
        existing.setMobileVerifiedAt(null);
        existing.setVerificationStatus(User.VerificationStatus.UNVERIFIED);
        existing.setAccountStatus(User.AccountStatus.PENDING_VERIFICATION);
        existing.setVerificationDeadline(Instant.now().plus(verificationDeadlineDuration));
        existing.setDownstreamProvisioned(false);
        existing.setDownstreamProvisionedAt(null);
        return existing;
    }

    private void auditRegistration(User savedUser, String sourceIp, String sourceUserAgent) {
        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.REGISTRATION);
        audit.setDescription("User registration completed");
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        audit.setUser(savedUser);
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);
    }

    private RegistrationResponse toResponse(User savedUser) {
        return new RegistrationResponse(
                savedUser.getId(),
                null,
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.isMfaEnabled());
    }

    private Optional<User> resolvePendingUser(String email, String phone) {
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        Optional<User> byPhone = userRepository.findByPhone(phone);

        if (byEmail.isPresent() && byPhone.isPresent() && !byEmail.get().getId().equals(byPhone.get().getId())) {
            throw new IllegalArgumentException("Identity is already registered");
        }

        Optional<User> existing = byEmail.isPresent() ? byEmail : byPhone;
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        ensurePendingReusable(existing.get(), "Identity is already registered");
        return existing;
    }

    private void ensurePendingReusable(User existing, String duplicateMessage) {
        if (existing.getAccountStatus() != User.AccountStatus.PENDING_VERIFICATION) {
            throw new IllegalArgumentException(duplicateMessage);
        }

        Instant deadline = existing.getVerificationDeadline();
        if (deadline != null && Instant.now().isAfter(deadline)) {
            throw new IllegalStateException("Pending registration expired. Restart registration");
        }
    }

}
