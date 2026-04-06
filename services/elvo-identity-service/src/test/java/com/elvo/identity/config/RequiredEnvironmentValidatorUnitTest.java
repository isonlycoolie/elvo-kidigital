package com.elvo.identity.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredEnvironmentValidatorUnitTest {

    @Test
    void shouldRejectInsecureDefaultsWhenStrictProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        RequiredEnvironmentValidator validator = new RequiredEnvironmentValidator(
                true,
                true,
                "dev,test,local",
                environment,
                "elvo",
                "guest",
                "sm://identity-jwt-secret",
                "change-this-otp-pepper",
                "replace_with_sms_api_key",
                "replace_with_email_password",
                "prod-token");

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateRequiredVariables);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("Insecure default environment values detected"));
    }

    @Test
    void shouldAllowInsecureDefaultsInRelaxedProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        RequiredEnvironmentValidator validator = new RequiredEnvironmentValidator(
                true,
                true,
                "dev,test,local",
                environment,
                "elvo",
                "guest",
                "sm://identity-jwt-secret",
                "change-this-otp-pepper",
                "replace_with_sms_api_key",
                "replace_with_email_password",
                "");

        assertDoesNotThrow(validator::validateRequiredVariables);
    }

    @Test
    void shouldRejectMissingRequiredValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        RequiredEnvironmentValidator validator = new RequiredEnvironmentValidator(
                true,
                true,
                "dev,test,local",
                environment,
                "",
                "guest",
                "sm://identity-jwt-secret",
                "",
                "replace_with_sms_api_key",
                "replace_with_email_password",
                "prod-token");

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateRequiredVariables);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().startsWith("Missing required environment variables"));
    }

    @Test
    void shouldRejectMissingProvisioningInternalTokenInStrictProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        RequiredEnvironmentValidator validator = new RequiredEnvironmentValidator(
                true,
                true,
                "dev,test,local",
                environment,
                "strong-db-password",
                "strong-rmq-password",
                "sm://identity-jwt-secret",
                "very-strong-otp-pepper",
                "valid-sms-key",
                "valid-email-password",
                "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validateRequiredVariables);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("ELVO_PROVISIONING_INTERNAL_AUTH_TOKEN"));
    }
}
