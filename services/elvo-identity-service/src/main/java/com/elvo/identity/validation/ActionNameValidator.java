package com.elvo.identity.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ActionNameValidator implements ConstraintValidator<ActionName, String> {

    private static final Pattern ACTION_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return ACTION_PATTERN.matcher(value.trim()).matches();
    }
}
