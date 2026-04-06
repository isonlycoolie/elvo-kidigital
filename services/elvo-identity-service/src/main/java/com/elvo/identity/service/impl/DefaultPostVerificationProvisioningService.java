package com.elvo.identity.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.User;
import com.elvo.identity.service.PostVerificationProvisioningService;

@Service
public class DefaultPostVerificationProvisioningService implements PostVerificationProvisioningService {

    @Override
    @Transactional
    public void provisionIfNeeded(User user) {
        if (user.isDownstreamProvisioned()) {
            return;
        }

        // Placeholder for ordered provisioning hooks: wallet, profile, preferences, session bootstrap.
        user.setDownstreamProvisioned(true);
        user.setDownstreamProvisionedAt(Instant.now());
    }
}
