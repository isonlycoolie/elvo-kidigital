package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.elvo.identity.client.AccountLifecycleClient;
import com.elvo.identity.client.ProfileProvisioningClient;
import com.elvo.identity.client.ProvisioningClientProperties;
import com.elvo.identity.client.WalletProvisioningClient;
import com.elvo.identity.entity.User;

class DefaultPostVerificationProvisioningServiceUnitTest {

    @Test
    void provisioningShouldBeIdempotent() {
        WalletProvisioningClient walletClient = mock(WalletProvisioningClient.class);
        AccountLifecycleClient accountLifecycleClient = mock(AccountLifecycleClient.class);
        ProfileProvisioningClient profileClient = mock(ProfileProvisioningClient.class);
        ProvisioningClientProperties properties = new ProvisioningClientProperties();
        properties.setProfileProvisioningEnabled(true);
        DefaultPostVerificationProvisioningService service =
                new DefaultPostVerificationProvisioningService(walletClient, accountLifecycleClient, profileClient, properties);
        User user = new User();

        service.provisionIfNeeded(user);
        Instant firstProvisionedAt = user.getDownstreamProvisionedAt();

        assertTrue(user.isDownstreamProvisioned());
        assertNotNull(firstProvisionedAt);

        service.provisionIfNeeded(user);

        assertEquals(firstProvisionedAt, user.getDownstreamProvisionedAt());
        verify(walletClient, times(1)).createWallet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(accountLifecycleClient, times(1)).syncPostVerification(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
        verify(profileClient, times(1)).createProfile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(profileClient, times(1)).createDefaultPreferences(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void provisioningFailureShouldKeepUserUnprovisioned() {
        WalletProvisioningClient walletClient = mock(WalletProvisioningClient.class);
        AccountLifecycleClient accountLifecycleClient = mock(AccountLifecycleClient.class);
        ProfileProvisioningClient profileClient = mock(ProfileProvisioningClient.class);
        ProvisioningClientProperties properties = new ProvisioningClientProperties();
        DefaultPostVerificationProvisioningService service =
                new DefaultPostVerificationProvisioningService(walletClient, accountLifecycleClient, profileClient, properties);
        User user = new User();

        doThrow(new RuntimeException("wallet unavailable"))
            .when(walletClient)
            .createWallet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThrows(IllegalStateException.class, () -> service.provisionIfNeeded(user));
        assertFalse(user.isDownstreamProvisioned());
    }
}
