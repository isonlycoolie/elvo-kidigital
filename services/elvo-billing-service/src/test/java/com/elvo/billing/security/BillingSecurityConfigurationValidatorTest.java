package com.elvo.billing.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingSecurityConfigurationValidatorTest {

    @Test
    void validatesSecureBillingConfiguration() {
        assertDoesNotThrow(() -> new BillingSecurityConfigurationValidator(
                "sm://billing-secret",
                "mock",
                "https://api.sandbox.selcom.example",
                "",
                "",
                1,
                3,
                900,
                604800,
                true,
                "OTP,AUTHENTICATOR_APP,HARDWARE_TOKEN"));
    }

    @Test
    void rejectsMissingInternalServiceSecret() {
        assertThrows(IllegalStateException.class, () -> new BillingSecurityConfigurationValidator(
                "",
                "mock",
                "https://api.sandbox.selcom.example",
                "",
                "",
                1,
                3,
                900,
                604800,
                true,
                "OTP,AUTHENTICATOR_APP,HARDWARE_TOKEN"));
    }

    @Test
    void rejectsRefreshTokenDurationShorterThanAccessTokenDuration() {
        assertThrows(IllegalStateException.class, () -> new BillingSecurityConfigurationValidator(
                "sm://billing-secret",
                "mock",
                "https://api.sandbox.selcom.example",
                "",
                "",
                1,
                3,
                900,
                60,
                true,
                "OTP,AUTHENTICATOR_APP,HARDWARE_TOKEN"));
    }
}
