package com.elvo.billing.monitoring;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SentrySensitiveDataMasker {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d\\s-]{7,}\\d)(?!\\d)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d{1,2})?\\b");

    public String maskReference(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    public String maskPhone(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() <= 3) {
            return "***";
        }
        return "***" + digits.substring(digits.length() - 3);
    }

    public String maskAmount(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return "***.**";
    }

    public String maskText(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }

        String masked = value;
        Matcher phoneMatcher = PHONE_PATTERN.matcher(masked);
        while (phoneMatcher.find()) {
            masked = masked.replace(phoneMatcher.group(), maskPhone(phoneMatcher.group()));
        }

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(masked);
        while (amountMatcher.find()) {
            masked = masked.replace(amountMatcher.group(), maskAmount(amountMatcher.group()));
        }

        return masked;
    }
}
