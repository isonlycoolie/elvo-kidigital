package com.elvo.billing.security;

import java.util.regex.Pattern;

public final class SensitiveDataMasker {

    private static final Pattern SENSITIVE_KV_PATTERN = Pattern.compile(
            "(?i)(password|token|secret|authorization|phone|account|receipt|reference|metadata)(\\s*[=:]\\s*)([^,;\\s]+)");

    private SensitiveDataMasker() {
    }

    public static String maskIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    public static String maskText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return SENSITIVE_KV_PATTERN.matcher(value).replaceAll("$1$2***");
    }
}