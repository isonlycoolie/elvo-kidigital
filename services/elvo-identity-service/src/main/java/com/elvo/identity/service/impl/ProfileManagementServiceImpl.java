package com.elvo.identity.service.impl;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.ProfileUpdateRequest;
import com.elvo.identity.dto.response.ProfileResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.entity.VerificationOtp;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.IdentityAccountReadService;
import com.elvo.identity.service.OtpService;
import com.elvo.identity.service.ProfileManagementService;
import com.elvo.identity.service.RecoveryCodeService;
import com.elvo.identity.service.TotpManagementService;

@Service
public class ProfileManagementServiceImpl implements ProfileManagementService {

    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final SecurityHashingService hashingService;
    private final AuditEventPublisher auditEventPublisher;
    private final IdentityAccountReadService accountReadService;
    private final TotpManagementService totpManagementService;
    private final RecoveryCodeService recoveryCodeService;
    private final OtpService otpService;

    public ProfileManagementServiceImpl(UserRepository userRepository,
                                        AuditRepository auditRepository,
                                        SecurityHashingService hashingService,
                                        AuditEventPublisher auditEventPublisher,
                                        IdentityAccountReadService accountReadService,
                                        TotpManagementService totpManagementService,
                                        RecoveryCodeService recoveryCodeService,
                                        OtpService otpService) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.hashingService = hashingService;
        this.auditEventPublisher = auditEventPublisher;
        this.accountReadService = accountReadService;
        this.totpManagementService = totpManagementService;
        this.recoveryCodeService = recoveryCodeService;
        this.otpService = otpService;
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(ProfileUpdateRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean emailChangeRequested = request.getEmail() != null && !request.getEmail().isBlank();
        boolean phoneChangeRequested = request.getPhone() != null && !request.getPhone().isBlank();
        boolean contactChangeRequested = emailChangeRequested || phoneChangeRequested;

        if (contactChangeRequested) {
            enforceContactUpdateVerification(user, request);
        }

        if (emailChangeRequested) {
            String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
            userRepository.findByEmailIgnoreCase(normalizedEmail)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Email is already in use");
                    });
            user.setEmail(normalizedEmail);
        }

        if (phoneChangeRequested) {
            String normalizedPhone = request.getPhone().trim();
            userRepository.findByPhone(normalizedPhone)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Phone is already in use");
                    });
            user.setPhone(normalizedPhone);
        }

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName().trim());
        }

        boolean passwordChanged = false;
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null || !hashingService.verifyPassword(request.getCurrentPassword(), user.getHashedPassword())) {
                throw new IllegalArgumentException("Current password is invalid");
            }
            user.setHashedPassword(hashingService.hashPassword(request.getNewPassword()));
            passwordChanged = true;
        }

        if (passwordChanged || contactChangeRequested) {
            enforceSensitiveActionMfa(user, request);
        }

        User savedUser = userRepository.save(user);

        Audit audit = new Audit();
        audit.setActionType(passwordChanged ? Audit.ActionType.PASSWORD_CHANGE : Audit.ActionType.USER_ACTIVITY);
        audit.setDescription(passwordChanged ? "User password changed" : "User profile updated");
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(request.getSourceIp());
        audit.setSourceUserAgent(request.getSourceUserAgent());
        audit.setUser(savedUser);
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);
        String resolvedEan = accountReadService.resolveEan(savedUser.getId());

        return new ProfileResponse(
                savedUser.getId(),
            resolvedEan,
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getDisplayName(),
                savedUser.isMfaEnabled(),
                savedUser.isEspEnabled());
    }

    private void enforceSensitiveActionMfa(User user, ProfileUpdateRequest request) {
        if (!user.isMfaEnabled()) {
            return;
        }
        String mfaCode = request.getMfaCode();
        if (mfaCode == null || mfaCode.isBlank()) {
            throw new IllegalArgumentException("MFA code is required for sensitive profile updates");
        }
        boolean totpVerified = totpManagementService.verifyActiveCode(user.getId(), mfaCode);
        if (totpVerified) {
            return;
        }
        boolean recoveryVerified = recoveryCodeService.consumeCode(user.getId(), mfaCode);
        if (!recoveryVerified) {
            throw new IllegalArgumentException("MFA verification failed for sensitive profile updates");
        }
    }

    private void enforceContactUpdateVerification(User user, ProfileUpdateRequest request) {
        if (request.getEmailVerificationCode() == null || request.getEmailVerificationCode().isBlank()
                || request.getMobileVerificationCode() == null || request.getMobileVerificationCode().isBlank()) {
            throw new IllegalArgumentException("Email and mobile verification codes are required for contact updates");
        }

        OtpService.OtpVerificationResult emailVerified = otpService.verifyOtp(
                user,
                VerificationOtp.Purpose.EMAIL_VERIFICATION,
                request.getEmailVerificationCode(),
                null,
                request.getSourceIp(),
                null);
        if (!emailVerified.success()) {
            throw new IllegalArgumentException("Email verification code is invalid");
        }

        OtpService.OtpVerificationResult mobileVerified = otpService.verifyOtp(
                user,
                VerificationOtp.Purpose.MOBILE_VERIFICATION,
                request.getMobileVerificationCode(),
                null,
                request.getSourceIp(),
                null);
        if (!mobileVerified.success()) {
            throw new IllegalArgumentException("Mobile verification code is invalid");
        }
    }
}
