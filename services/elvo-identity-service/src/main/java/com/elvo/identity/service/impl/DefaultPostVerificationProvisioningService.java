package com.elvo.identity.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.elvo.identity.client.AccountLifecycleClient;
import com.elvo.identity.client.ProfileProvisioningClient;
import com.elvo.identity.client.ProvisioningClientProperties;
import com.elvo.identity.client.WalletProvisioningClient;
import com.elvo.identity.entity.User;
import com.elvo.identity.service.PostVerificationProvisioningService;

@Service
public class DefaultPostVerificationProvisioningService implements PostVerificationProvisioningService {

    private final WalletProvisioningClient walletProvisioningClient;
    private final AccountLifecycleClient accountLifecycleClient;
    private final ProfileProvisioningClient profileProvisioningClient;
    private final ProvisioningClientProperties provisioningClientProperties;

    public DefaultPostVerificationProvisioningService(WalletProvisioningClient walletProvisioningClient,
                                                      AccountLifecycleClient accountLifecycleClient,
                                                      ProfileProvisioningClient profileProvisioningClient,
                                                      ProvisioningClientProperties provisioningClientProperties) {
        this.walletProvisioningClient = walletProvisioningClient;
        this.accountLifecycleClient = accountLifecycleClient;
        this.profileProvisioningClient = profileProvisioningClient;
        this.provisioningClientProperties = provisioningClientProperties;
    }

    @Override
    public void provisionIfNeeded(User user) {
        if (user.isDownstreamProvisioned()) {
            return;
        }

        String keyPrefix = "identity:" + user.getId() + ":post-verification:";
        try {
            walletProvisioningClient.createWallet(user.getId(), keyPrefix + "wallet");
            accountLifecycleClient.syncPostVerification(
                    user.getId(),
                    user.isEmailVerified(),
                    user.isMobileVerified(),
                    keyPrefix + "account-sync");

            if (provisioningClientProperties.isProfileProvisioningEnabled()) {
                profileProvisioningClient.createProfile(user.getId(), keyPrefix + "profile");
                profileProvisioningClient.createDefaultPreferences(user.getId(), keyPrefix + "preferences");
            }

            user.setDownstreamProvisioned(true);
            user.setDownstreamProvisionedAt(Instant.now());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Post-verification provisioning failed for user " + user.getId(), ex);
        }
    }
}
