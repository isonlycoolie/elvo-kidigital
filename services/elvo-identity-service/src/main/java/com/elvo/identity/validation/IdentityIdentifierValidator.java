package com.elvo.identity.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IdentityIdentifierValidator implements ConstraintValidator<IdentityIdentifier, String> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+]{7,20}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim();
        return EMAIL_PATTERN.matcher(normalized).matches() || PHONE_PATTERN.matcher(normalized).matches();
    }
}
