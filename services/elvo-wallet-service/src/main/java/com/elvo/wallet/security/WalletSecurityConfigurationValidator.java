package com.elvo.wallet.security;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class WalletSecurityConfigurationValidator {

    private final InternalServiceJwtProperties internalServiceJwtProperties;
    private final UserJwtProperties userJwtProperties;
    private final InternalServiceAuthorizationProperties internalServiceAuthorizationProperties;
    private final MutualTlsProperties mutualTlsProperties;
    private final Environment environment;

    public WalletSecurityConfigurationValidator(InternalServiceJwtProperties internalServiceJwtProperties,
                                                UserJwtProperties userJwtProperties,
                                                InternalServiceAuthorizationProperties internalServiceAuthorizationProperties,
                                                MutualTlsProperties mutualTlsProperties,
                                                Environment environment) {
        this.internalServiceJwtProperties = internalServiceJwtProperties;
        this.userJwtProperties = userJwtProperties;
        this.internalServiceAuthorizationProperties = internalServiceAuthorizationProperties;
        this.mutualTlsProperties = mutualTlsProperties;
        this.environment = environment;
        validate();
    }

    @PostConstruct
    public void validate() {
        requirePresent(internalServiceJwtProperties.getSecret(), "elvo.security.internal-jwt.secret");
        requirePresent(internalServiceJwtProperties.getIssuer(), "elvo.security.internal-jwt.issuer");
        requirePresent(internalServiceJwtProperties.getAudience(), "elvo.security.internal-jwt.audience");
        requirePresent(userJwtProperties.getIssuer(), "elvo.security.jwt.issuer");
        requirePresent(userJwtProperties.getAudience(), "elvo.security.jwt.audience");
        requirePresent(userJwtProperties.getSigningKeyId(), "elvo.security.jwt.signing-key-id");
        if (isBlank(userJwtProperties.getSecret()) && isBlank(userJwtProperties.getSigningPublicKeyPem())) {
            throw new IllegalStateException("elvo.security.jwt.secret or elvo.security.jwt.signing-public-key-pem must be configured");
        }

        if (mutualTlsProperties.isEnabledForProfiles(environment.getActiveProfiles())) {
            if (mutualTlsProperties.getTrustedCommonNames() == null || mutualTlsProperties.getTrustedCommonNames().isEmpty()) {
                throw new IllegalStateException("elvo.security.mtls.trusted-common-names must be configured when mTLS is enabled");
            }
            requirePresent(mutualTlsProperties.getSubjectPrincipalRegex(), "elvo.security.mtls.subject-principal-regex");
        }

        if (internalServiceAuthorizationProperties.getServiceRules().isEmpty()) {
            throw new IllegalStateException("elvo.security.internal-authz.service-rules must be configured");
        }
    }

    private static void requirePresent(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
