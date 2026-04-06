package com.elvo.identity.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.client.ProfileProvisioningClient;
import com.elvo.identity.client.WalletProvisioningClient;
import com.elvo.identity.entity.User;
import com.elvo.identity.service.PostVerificationProvisioningService;

@Service
public class DefaultPostVerificationProvisioningService implements PostVerificationProvisioningService {

    private final WalletProvisioningClient walletProvisioningClient;
    private final ProfileProvisioningClient profileProvisioningClient;

    public DefaultPostVerificationProvisioningService(WalletProvisioningClient walletProvisioningClient,
                                                      ProfileProvisioningClient profileProvisioningClient) {
        this.walletProvisioningClient = walletProvisioningClient;
        this.profileProvisioningClient = profileProvisioningClient;
    }

    @Override
    @Transactional
    public void provisionIfNeeded(User user) {
        if (user.isDownstreamProvisioned()) {
            return;
        }

        String keyPrefix = "identity:" + user.getId() + ":post-verification:";
        walletProvisioningClient.createWallet(user.getId(), keyPrefix + "wallet");
        profileProvisioningClient.createProfile(user.getId(), keyPrefix + "profile");
        profileProvisioningClient.createDefaultPreferences(user.getId(), keyPrefix + "preferences");
        user.setDownstreamProvisioned(true);
        user.setDownstreamProvisionedAt(Instant.now());
    }
}
