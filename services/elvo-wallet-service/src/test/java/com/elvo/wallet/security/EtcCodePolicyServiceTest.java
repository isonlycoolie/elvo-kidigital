package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class EtcCodePolicyServiceTest {

    @Test
    void shouldGenerateSecureCodeWithConfiguredLength() {
        EtcCodePolicyService policyService = new EtcCodePolicyService(12, 900);

        String code = policyService.generateSecureCode();

        assertThat(code).startsWith("ETC-");
        assertThat(code.substring(4)).hasSize(12);
        assertThat(policyService.hasRequiredEntropy(code)).isTrue();
    }

    @Test
    void shouldRejectShortOrInvalidEntropyCode() {
        EtcCodePolicyService policyService = new EtcCodePolicyService(12, 900);

        assertThat(policyService.hasRequiredEntropy("ETC-ABC123")).isFalse();
        assertThat(policyService.hasRequiredEntropy("ETC-abcd1234abcd")).isFalse();
        assertThat(policyService.hasRequiredEntropy("NOPE-ABCDEFGHIJKL")).isFalse();
    }

    @Test
    void shouldEnforceStrictExpiryWindow() {
        EtcCodePolicyService policyService = new EtcCodePolicyService(12, 900);
        Instant now = Instant.parse("2026-04-06T00:00:00Z");

        assertThat(policyService.isExpiryWithinWindow(now.plusSeconds(1), now)).isTrue();
        assertThat(policyService.isExpiryWithinWindow(now.plusSeconds(900), now)).isTrue();
        assertThat(policyService.isExpiryWithinWindow(now.plusSeconds(901), now)).isFalse();
        assertThat(policyService.isExpiryWithinWindow(now.minusSeconds(1), now)).isFalse();
    }
}
