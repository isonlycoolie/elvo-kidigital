package com.elvo.wallet.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletSecurityConfigurationValidatorTest {

    @Test
    void validatesSecureWalletConfiguration() {
        InternalServiceJwtProperties internalServiceJwtProperties = new InternalServiceJwtProperties();
        internalServiceJwtProperties.setSecret("wallet-internal-secret");
        internalServiceJwtProperties.setIssuer("wallet-issuer");
        internalServiceJwtProperties.setAudience("wallet-audience");

        UserJwtProperties userJwtProperties = new UserJwtProperties();
        userJwtProperties.setSecret("user-signing-secret");
        userJwtProperties.setIssuer("wallet-user-issuer");
        userJwtProperties.setAudience("wallet-user-audience");
        userJwtProperties.setSigningKeyId("kid-1");

        InternalServiceAuthorizationProperties authzProperties = new InternalServiceAuthorizationProperties();
        authzProperties.setServiceRules(Map.of("wallet-service", List.of("CONSUME:/api/v1/internal/wallets/**")));

        MutualTlsProperties mtlsProperties = new MutualTlsProperties();
        mtlsProperties.setEnabled(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertDoesNotThrow(() -> new WalletSecurityConfigurationValidator(
                internalServiceJwtProperties,
                userJwtProperties,
                authzProperties,
                mtlsProperties,
                environment));
    }

    @Test
    void rejectsMissingJwtSecret() {
        InternalServiceJwtProperties internalServiceJwtProperties = new InternalServiceJwtProperties();
        internalServiceJwtProperties.setIssuer("wallet-issuer");
        internalServiceJwtProperties.setAudience("wallet-audience");

        UserJwtProperties userJwtProperties = new UserJwtProperties();
        userJwtProperties.setSecret("user-signing-secret");
        userJwtProperties.setIssuer("wallet-user-issuer");
        userJwtProperties.setAudience("wallet-user-audience");
        userJwtProperties.setSigningKeyId("kid-1");

        InternalServiceAuthorizationProperties authzProperties = new InternalServiceAuthorizationProperties();
        authzProperties.setServiceRules(Map.of("wallet-service", List.of("CONSUME:/api/v1/internal/wallets/**")));

        MutualTlsProperties mtlsProperties = new MutualTlsProperties();
        mtlsProperties.setEnabled(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertThrows(IllegalStateException.class, () -> new WalletSecurityConfigurationValidator(
                internalServiceJwtProperties,
                userJwtProperties,
                authzProperties,
                mtlsProperties,
                environment));
    }

    @Test
    void rejectsEmptyAuthorizationRules() {
        InternalServiceJwtProperties internalServiceJwtProperties = new InternalServiceJwtProperties();
        internalServiceJwtProperties.setSecret("wallet-internal-secret");
        internalServiceJwtProperties.setIssuer("wallet-issuer");
        internalServiceJwtProperties.setAudience("wallet-audience");

        UserJwtProperties userJwtProperties = new UserJwtProperties();
        userJwtProperties.setSecret("user-signing-secret");
        userJwtProperties.setIssuer("wallet-user-issuer");
        userJwtProperties.setAudience("wallet-user-audience");
        userJwtProperties.setSigningKeyId("kid-1");

        InternalServiceAuthorizationProperties authzProperties = new InternalServiceAuthorizationProperties();

        MutualTlsProperties mtlsProperties = new MutualTlsProperties();
        mtlsProperties.setEnabled(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertThrows(IllegalStateException.class, () -> new WalletSecurityConfigurationValidator(
                internalServiceJwtProperties,
                userJwtProperties,
                authzProperties,
                mtlsProperties,
                environment));
    }
}
