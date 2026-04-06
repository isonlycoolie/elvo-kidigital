package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EtcCodeSecurityServiceTest {

    @Test
    void shouldRejectEmptyPepper() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new EtcCodeSecurityService("")
        );

        assertThat(ex.getMessage()).contains("must be securely configured");
    }

    @Test
    void shouldRejectHardcodedPepper() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> new EtcCodeSecurityService("elvo-wallet-etc-pepper")
        );

        assertThat(ex.getMessage()).contains("Hardcoded or default pepper values are not permitted");
    }

    @Test
    void shouldAcceptManagedPepper() {
        EtcCodeSecurityService service = new EtcCodeSecurityService("sm://wallet-etc-hash-pepper");

        String hash = service.hashCode("123456789012");
        assertThat(hash).isNotBlank();
        assertThat(service.redact("123456789012")).isEqualTo("***9012");
    }
}
