package com.elvo.identity.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class SecretManagerService {

    private static final String SECRET_PREFIX = "sm://";
    private static final String SECRET_PROPERTY_PREFIX = "elvo.secret-manager.secrets.";

    private final Environment environment;

    public SecretManagerService(Environment environment) {
        this.environment = environment;
    }

    public String resolve(String secretName,
                          String configuredValue,
                          String fallbackEnvVariable,
                          String fallbackValue) {
        String trimmedConfigured = normalize(configuredValue);
        if (trimmedConfigured != null && !trimmedConfigured.startsWith(SECRET_PREFIX)) {
            return trimmedConfigured;
        }

        String key = secretName;
        if (trimmedConfigured != null && trimmedConfigured.startsWith(SECRET_PREFIX)) {
            String referenced = normalize(trimmedConfigured.substring(SECRET_PREFIX.length()));
            if (referenced != null) {
                key = referenced;
            }
        }

        String managedSecret = normalize(environment.getProperty(SECRET_PROPERTY_PREFIX + key));
        if (managedSecret != null) {
            return managedSecret;
        }

        if (fallbackEnvVariable != null && !fallbackEnvVariable.isBlank()) {
            String envSecret = normalize(environment.getProperty(fallbackEnvVariable));
            if (envSecret != null) {
                return envSecret;
            }
        }

        return fallbackValue;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
