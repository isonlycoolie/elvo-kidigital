package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class EmergencyControlServiceTest {

    @Test
    void shouldEnableAndDisableGlobalKillSwitch() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        EmergencyControlService service = new EmergencyControlService(provider, "wallet:emergency:");
        service.setGlobalKillSwitch(true, "incident");
        assertThat(service.isGlobalKillSwitchEnabled()).isTrue();
        assertThat(service.globalKillSwitchReason()).isEqualTo("incident");

        service.setGlobalKillSwitch(false, "resolved");
        assertThat(service.isGlobalKillSwitchEnabled()).isFalse();
    }

    @Test
    void shouldTrackEmergencyFreezePerWallet() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        EmergencyControlService service = new EmergencyControlService(provider, "wallet:emergency:");
        UUID walletId = UUID.randomUUID();

        service.freezeWalletEmergency(walletId, "fraud incident");
        assertThat(service.isWalletEmergencyFrozen(walletId)).isTrue();
        assertThat(service.walletEmergencyReason(walletId)).contains("fraud");

        service.unfreezeWalletEmergency(walletId);
        assertThat(service.isWalletEmergencyFrozen(walletId)).isFalse();
    }
}
