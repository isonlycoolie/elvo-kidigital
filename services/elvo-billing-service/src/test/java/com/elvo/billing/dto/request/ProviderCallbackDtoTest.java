package com.elvo.billing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ProviderCallbackDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validationShouldRequireMandatoryFields() {
        ProviderCallbackDto dto = new ProviderCallbackDto();

        Set<ConstraintViolation<ProviderCallbackDto>> violations = validator.validate(dto);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("externalReference is required", "status is required", "referenceNumber is required");
    }

    @Test
    void metadataSetterShouldNormalizeBlankToDefaultJson() {
        ProviderCallbackDto dto = new ProviderCallbackDto();
        dto.setMetadata("   ");

        assertThat(dto.getMetadata()).isEqualTo("{}");
    }

    @Test
    void metadataSetterShouldKeepProvidedJson() {
        ProviderCallbackDto dto = new ProviderCallbackDto();
        dto.setMetadata("{\"callback\":true}");

        assertThat(dto.getMetadata()).isEqualTo("{\"callback\":true}");
    }
}