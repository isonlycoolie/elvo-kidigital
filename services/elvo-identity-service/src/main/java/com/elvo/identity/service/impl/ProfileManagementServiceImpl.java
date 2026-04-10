package com.elvo.identity.service.impl;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.ProfileUpdateRequest;
import com.elvo.identity.dto.response.ProfileResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.IdentityAccountReadService;
import com.elvo.identity.service.ProfileManagementService;

@Service
public class ProfileManagementServiceImpl implements ProfileManagementService {

    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final SecurityHashingService hashingService;
    private final AuditEventPublisher auditEventPublisher;
    private final IdentityAccountReadService accountReadService;

    public ProfileManagementServiceImpl(UserRepository userRepository,
                                        AuditRepository auditRepository,
                                        SecurityHashingService hashingService,
                                        AuditEventPublisher auditEventPublisher,
                                        IdentityAccountReadService accountReadService) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.hashingService = hashingService;
        this.auditEventPublisher = auditEventPublisher;
        this.accountReadService = accountReadService;
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(ProfileUpdateRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
            userRepository.findByEmailIgnoreCase(normalizedEmail)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Email is already in use");
                    });
            user.setEmail(normalizedEmail);
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
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
}
