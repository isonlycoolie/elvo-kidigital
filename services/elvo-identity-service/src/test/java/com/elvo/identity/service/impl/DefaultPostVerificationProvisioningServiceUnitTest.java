package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.elvo.identity.client.ProfileProvisioningClient;
import com.elvo.identity.client.WalletProvisioningClient;
import com.elvo.identity.entity.User;

class DefaultPostVerificationProvisioningServiceUnitTest {

    @Test
    void provisioningShouldBeIdempotent() {
        WalletProvisioningClient walletClient = mock(WalletProvisioningClient.class);
        ProfileProvisioningClient profileClient = mock(ProfileProvisioningClient.class);
        DefaultPostVerificationProvisioningService service = new DefaultPostVerificationProvisioningService(walletClient, profileClient);
        User user = new User();

        service.provisionIfNeeded(user);
        Instant firstProvisionedAt = user.getDownstreamProvisionedAt();

        assertTrue(user.isDownstreamProvisioned());
        assertNotNull(firstProvisionedAt);

        service.provisionIfNeeded(user);

        assertEquals(firstProvisionedAt, user.getDownstreamProvisionedAt());
        verify(walletClient, times(1)).createWallet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(profileClient, times(1)).createProfile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(profileClient, times(1)).createDefaultPreferences(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void provisioningFailureShouldKeepUserUnprovisioned() {
        WalletProvisioningClient walletClient = mock(WalletProvisioningClient.class);
        ProfileProvisioningClient profileClient = mock(ProfileProvisioningClient.class);
        DefaultPostVerificationProvisioningService service = new DefaultPostVerificationProvisioningService(walletClient, profileClient);
        User user = new User();

        doThrow(new RuntimeException("wallet unavailable"))
            .when(walletClient)
            .createWallet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThrows(IllegalStateException.class, () -> service.provisionIfNeeded(user));
        assertFalse(user.isDownstreamProvisioned());
    }
}
