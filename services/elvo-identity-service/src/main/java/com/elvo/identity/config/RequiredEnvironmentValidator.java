package com.elvo.identity.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RequiredEnvironmentValidator {

    private final boolean enabled;
    private final boolean rejectInsecureDefaults;
    private final Set<String> relaxedProfiles;
    private final Environment environment;
    private final String provisioningInternalAuthToken;
    private final Map<String, String> requiredValues;
    private final Map<String, Set<String>> insecureDefaults;

    public RequiredEnvironmentValidator(
            @Value("${elvo.startup.validate-required-env:true}") boolean enabled,
            @Value("${elvo.startup.reject-insecure-defaults:true}") boolean rejectInsecureDefaults,
            @Value("${elvo.startup.relaxed-profiles:dev,test,local}") String relaxedProfilesCsv,
            Environment environment,
            @Value("${ELVO_DB_PASSWORD:elvo}") String dbPassword,
            @Value("${ELVO_RABBITMQ_PASSWORD:guest}") String rabbitmqPassword,
            @Value("${ELVO_JWT_SECRET_REF:sm://identity-jwt-secret}") String jwtSecretRef,
            @Value("${ELVO_TOTP_ENCRYPTION_SECRET_REF:sm://identity-totp-encryption-secret}") String totpEncryptionSecretRef,
            @Value("${ELVO_OTP_HASH_PEPPER:change-this-otp-pepper}") String otpHashPepper,
            @Value("${SMS_PROVIDER_API_KEY:replace_with_sms_api_key}") String smsApiKey,
            @Value("${EMAIL_PROVIDER_PASSWORD:replace_with_email_password}") String emailProviderPassword,
            @Value("${ELVO_PROVISIONING_INTERNAL_AUTH_TOKEN:}") String provisioningInternalAuthToken) {
        this.enabled = enabled;
        this.rejectInsecureDefaults = rejectInsecureDefaults;
        this.relaxedProfiles = parseProfiles(relaxedProfilesCsv);
        this.environment = environment;
        this.provisioningInternalAuthToken = provisioningInternalAuthToken;
        this.requiredValues = new LinkedHashMap<>();
        this.insecureDefaults = new LinkedHashMap<>();
        requiredValues.put("ELVO_DB_PASSWORD", dbPassword);
        requiredValues.put("ELVO_RABBITMQ_PASSWORD", rabbitmqPassword);
        requiredValues.put("ELVO_JWT_SECRET_REF", jwtSecretRef);
        requiredValues.put("ELVO_TOTP_ENCRYPTION_SECRET_REF", totpEncryptionSecretRef);
        requiredValues.put("ELVO_OTP_HASH_PEPPER", otpHashPepper);
        requiredValues.put("SMS_PROVIDER_API_KEY", smsApiKey);
        requiredValues.put("EMAIL_PROVIDER_PASSWORD", emailProviderPassword);

        insecureDefaults.put("ELVO_DB_PASSWORD", Set.of("elvo", "password", "changeme", "change-me"));
        insecureDefaults.put("ELVO_RABBITMQ_PASSWORD", Set.of("guest"));
        insecureDefaults.put("ELVO_TOTP_ENCRYPTION_SECRET_REF", Set.of("change-this-totp-encryption-secret", "changeme", "change-me"));
        insecureDefaults.put("ELVO_OTP_HASH_PEPPER", Set.of("change-this-otp-pepper", "changeme", "change-me"));
        insecureDefaults.put("SMS_PROVIDER_API_KEY", Set.of("replace_with_sms_api_key", "replace-with-sms-api-key"));
        insecureDefaults.put("EMAIL_PROVIDER_PASSWORD", Set.of("replace_with_email_password", "replace-with-email-password"));
    }

    @PostConstruct
    public void validateRequiredVariables() {
        if (!enabled) {
            return;
        }

        boolean strictValidation = shouldEnforceStrictValidation();

        StringBuilder missing = new StringBuilder();
        requiredValues.forEach((name, value) -> {
            if (value == null || value.isBlank()) {
                if (!missing.isEmpty()) {
                    missing.append(", ");
                }
                missing.append(name);
            }
        });

        if (strictValidation && (provisioningInternalAuthToken == null || provisioningInternalAuthToken.isBlank())) {
            if (!missing.isEmpty()) {
                missing.append(", ");
            }
            missing.append("ELVO_PROVISIONING_INTERNAL_AUTH_TOKEN");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required environment variables: " + missing);
        }

        if (rejectInsecureDefaults && strictValidation) {
            StringBuilder insecure = new StringBuilder();
            insecureDefaults.forEach((name, defaults) -> {
                String value = requiredValues.get(name);
                if (value != null && defaults.contains(value.trim().toLowerCase())) {
                    if (!insecure.isEmpty()) {
                        insecure.append(", ");
                    }
                    insecure.append(name);
                }
            });
            if (!insecure.isEmpty()) {
                throw new IllegalStateException("Insecure default environment values detected: " + insecure);
            }
        }
    }

    private boolean shouldEnforceStrictValidation() {
        for (String profile : environment.getActiveProfiles()) {
            if (relaxedProfiles.contains(profile.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private Set<String> parseProfiles(String relaxedProfilesCsv) {
        return Arrays.stream(relaxedProfilesCsv.split(","))
            .map(String::trim)
            .filter(profile -> !profile.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }
}
