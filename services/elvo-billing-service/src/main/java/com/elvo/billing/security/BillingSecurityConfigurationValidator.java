package com.elvo.billing.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BillingSecurityConfigurationValidator {

    private final String internalServiceSecret;
    private final String defaultProvider;
    private final String selcomBaseUrl;
    private final String selcomApiKey;
    private final String selcomSecret;
    private final int criticalErrorThreshold;
    private final int adapterFailureThreshold;
    private final long accessTokenDurationSeconds;
    private final long refreshTokenDurationSeconds;
    private final boolean mfaRequiredForSensitivePermissions;
    private final String mfaAllowedMethods;

    public BillingSecurityConfigurationValidator(
            @Value("${elvo.security.internal-service-secret:}") String internalServiceSecret,
            @Value("${elvo.billing.adapters.default-provider:}") String defaultProvider,
            @Value("${elvo.billing.adapters.providers.selcom.base-url:}") String selcomBaseUrl,
            @Value("${elvo.billing.adapters.providers.selcom.api-key:}") String selcomApiKey,
            @Value("${elvo.billing.adapters.providers.selcom.secret:}") String selcomSecret,
            @Value("${elvo.billing.sentry.alerts.critical-error-threshold:1}") int criticalErrorThreshold,
            @Value("${elvo.billing.sentry.alerts.adapter-failure-threshold:3}") int adapterFailureThreshold,
            @Value("${elvo.security.session.access-token-duration-seconds:900}") long accessTokenDurationSeconds,
            @Value("${elvo.security.session.refresh-token-duration-seconds:604800}") long refreshTokenDurationSeconds,
            @Value("${elvo.security.mfa.required-for-sensitive-permissions:true}") boolean mfaRequiredForSensitivePermissions,
            @Value("${elvo.security.mfa.allowed-methods:OTP,AUTHENTICATOR_APP,HARDWARE_TOKEN}") String mfaAllowedMethods) {
        this.internalServiceSecret = internalServiceSecret;
        this.defaultProvider = defaultProvider;
        this.selcomBaseUrl = selcomBaseUrl;
        this.selcomApiKey = selcomApiKey;
        this.selcomSecret = selcomSecret;
        this.criticalErrorThreshold = criticalErrorThreshold;
        this.adapterFailureThreshold = adapterFailureThreshold;
        this.accessTokenDurationSeconds = accessTokenDurationSeconds;
        this.refreshTokenDurationSeconds = refreshTokenDurationSeconds;
        this.mfaRequiredForSensitivePermissions = mfaRequiredForSensitivePermissions;
        this.mfaAllowedMethods = mfaAllowedMethods;
        validate();
    }

    @PostConstruct
    public void validate() {
        requirePresent(internalServiceSecret, "elvo.security.internal-service-secret");
        requirePresent(defaultProvider, "elvo.billing.adapters.default-provider");
        requirePositive(criticalErrorThreshold, "elvo.billing.sentry.alerts.critical-error-threshold");
        requirePositive(adapterFailureThreshold, "elvo.billing.sentry.alerts.adapter-failure-threshold");
        requirePositive(accessTokenDurationSeconds, "elvo.security.session.access-token-duration-seconds");
        requirePositive(refreshTokenDurationSeconds, "elvo.security.session.refresh-token-duration-seconds");
        if (refreshTokenDurationSeconds <= accessTokenDurationSeconds) {
            throw new IllegalStateException("elvo.security.session.refresh-token-duration-seconds must be greater than access token duration");
        }
        if (mfaRequiredForSensitivePermissions) {
            requirePresent(mfaAllowedMethods, "elvo.security.mfa.allowed-methods");
        }
        if (!"mock".equalsIgnoreCase(defaultProvider)) {
            requirePresent(selcomBaseUrl, "elvo.billing.adapters.providers.selcom.base-url");
            requirePresent(selcomApiKey, "elvo.billing.adapters.providers.selcom.api-key");
            requirePresent(selcomSecret, "elvo.billing.adapters.providers.selcom.secret");
        }
    }

    private static void requirePresent(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
    }

    private static void requirePositive(long value, String propertyName) {
        if (value <= 0) {
            throw new IllegalStateException(propertyName + " must be greater than zero");
        }
    }
}
