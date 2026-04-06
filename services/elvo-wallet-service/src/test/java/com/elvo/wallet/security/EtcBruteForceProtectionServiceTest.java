package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EtcBruteForceProtectionServiceTest {

    @Test
    void shouldBlockAfterConfiguredFailures() {
        EtcBruteForceProtectionService service = new EtcBruteForceProtectionService(2, 10, 10, 900);

        String codeHash = "code-hash-1";
        String deviceId = "device-1";
        String ip = "10.0.0.1";

        assertThat(service.isBlocked(codeHash, deviceId, ip)).isFalse();
        assertThat(service.registerFailure(codeHash, deviceId, ip)).isFalse();
        assertThat(service.isBlocked(codeHash, deviceId, ip)).isFalse();

        assertThat(service.registerFailure(codeHash, deviceId, ip)).isTrue();
        assertThat(service.isBlocked(codeHash, deviceId, ip)).isTrue();
    }

    @Test
    void shouldClearCountersOnSuccess() {
        EtcBruteForceProtectionService service = new EtcBruteForceProtectionService(2, 2, 2, 900);

        String codeHash = "code-hash-2";
        String deviceId = "device-2";
        String ip = "10.0.0.2";

        service.registerFailure(codeHash, deviceId, ip);
        service.registerFailure(codeHash, deviceId, ip);
        assertThat(service.isBlocked(codeHash, deviceId, ip)).isTrue();

        service.clearOnSuccess(codeHash, deviceId, ip);
        assertThat(service.isBlocked(codeHash, deviceId, ip)).isFalse();
    }
}
