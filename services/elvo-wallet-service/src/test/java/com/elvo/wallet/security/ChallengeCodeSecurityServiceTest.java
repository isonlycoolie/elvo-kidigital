package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChallengeCodeSecurityServiceTest {

    @Test
    void shouldRejectEmptyPepper() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new ChallengeCodeSecurityService("")
        );

        assertThat(ex.getMessage()).contains("must be securely configured");
    }

    @Test
    void shouldRejectHardcodedPepper() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new ChallengeCodeSecurityService("elvo-wallet-challenge-pepper")
        );

        assertThat(ex.getMessage()).contains("Hardcoded or default pepper values are not permitted");
    }

    @Test
    void shouldGenerateAndHashValidCode() {
        ChallengeCodeSecurityService service = new ChallengeCodeSecurityService("sm://wallet-challenge-hash-pepper");

        String code = service.generateCode();
        assertThat(code).matches("\\d{4}");
        assertThat(service.isValidFormat(code)).isTrue();
        assertThat(service.hashCode(code)).isNotBlank();
        assertThat(service.redact(code)).startsWith("***");
    }
}
