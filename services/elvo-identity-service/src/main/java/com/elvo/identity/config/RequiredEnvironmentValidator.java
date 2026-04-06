package com.elvo.identity.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RequiredEnvironmentValidator {

    private final boolean enabled;
    private final Map<String, String> requiredValues;

    public RequiredEnvironmentValidator(
            @Value("${elvo.startup.validate-required-env:true}") boolean enabled,
            @Value("${ELVO_DB_PASSWORD:elvo}") String dbPassword,
            @Value("${ELVO_JWT_SECRET_REF:sm://identity-jwt-secret}") String jwtSecretRef,
            @Value("${ELVO_OTP_HASH_PEPPER:change-this-otp-pepper}") String otpHashPepper,
            @Value("${SMS_PROVIDER_API_KEY:replace_with_sms_api_key}") String smsApiKey,
            @Value("${EMAIL_PROVIDER_PASSWORD:replace_with_email_password}") String emailProviderPassword) {
        this.enabled = enabled;
        this.requiredValues = new LinkedHashMap<>();
        requiredValues.put("ELVO_DB_PASSWORD", dbPassword);
        requiredValues.put("ELVO_JWT_SECRET_REF", jwtSecretRef);
        requiredValues.put("ELVO_OTP_HASH_PEPPER", otpHashPepper);
        requiredValues.put("SMS_PROVIDER_API_KEY", smsApiKey);
        requiredValues.put("EMAIL_PROVIDER_PASSWORD", emailProviderPassword);
    }

    @PostConstruct
    public void validateRequiredVariables() {
        if (!enabled) {
            return;
        }

        StringBuilder missing = new StringBuilder();
        requiredValues.forEach((name, value) -> {
            if (value == null || value.isBlank()) {
                if (!missing.isEmpty()) {
                    missing.append(", ");
                }
                missing.append(name);
            }
        });

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required environment variables: " + missing);
        }
    }
}
