package com.elvo.identity.service.impl;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.dto.request.RegistrationRequest;
import com.elvo.identity.dto.response.RegistrationResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.User;
import com.elvo.identity.exception.ApiResponse;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.SecurityHashingService;
import com.elvo.identity.service.RegistrationService;
import com.elvo.identity.util.EanGenerator;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final EanGenerator eanGenerator;
    private final SecurityHashingService hashingService;

    public RegistrationServiceImpl(UserRepository userRepository,
                                   AuditRepository auditRepository,
                                   EanGenerator eanGenerator,
                                   SecurityHashingService hashingService) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.eanGenerator = eanGenerator;
        this.hashingService = hashingService;
    }

    @Override
    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        validateUniqueIdentity(request);

        User user = new User();
        user.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        user.setPhone(request.getPhone().trim());
        user.setHashedPassword(hashingService.hashPassword(request.getPassword()));
        user.setMfaEnabled(request.isEnableMfa());
        user.setEspEnabled(false);
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        user.setEan(generateUniqueEan());

        User savedUser = userRepository.save(user);

        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.REGISTRATION);
        audit.setDescription("User registration completed");
        audit.setSourceType(Audit.SourceType.API);
        audit.setSourceIp(request.getSourceIp());
        audit.setSourceUserAgent(request.getSourceUserAgent());
        audit.setUser(savedUser);
        auditRepository.save(audit);

        return new RegistrationResponse(
                savedUser.getId(),
                savedUser.getEan(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.isMfaEnabled());
    }

    private void validateUniqueIdentity(RegistrationRequest request) {
        if (userRepository.findByEmailIgnoreCase(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email is already registered");
        }

        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new IllegalArgumentException("Phone is already registered");
        }
    }

    private String generateUniqueEan() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String ean = eanGenerator.generate();
            if (userRepository.findByEan(ean).isEmpty()) {
                return ean;
            }
        }
        throw new IllegalStateException("Unable to generate a unique EAN");
    }
}
