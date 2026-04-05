package com.elvo.identity.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DeviceIdentifierValidator implements ConstraintValidator<DeviceIdentifier, String> {

    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{6,128}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return DEVICE_ID_PATTERN.matcher(value.trim()).matches();
    }
}
