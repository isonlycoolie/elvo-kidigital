package com.elvo.billing.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.exception.PaymentValidationException;

class UtilityPaymentValidationBuilderTest {

    @Test
    void shouldNormalizeMetadataToEmptyJson() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-100");
        request.setLookupRequired(true);
        request.setMetadata("   ");

        UtilityPaymentValidationBuilder.forRequest(request)
                .normalizeOptionalMetadata();

        assertEquals("{}", request.getMetadata());
    }

    @Test
    void shouldThrowFieldErrorsForMissingBaseAndCategoryFields() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setLookupRequired(false);
        request.setMetadata("{}");

        PaymentValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(
                PaymentValidationException.class,
                () -> UtilityPaymentValidationBuilder.forRequest(request)
                        .withBillCategory(BillCategory.GOVERNMENT)
                        .validateBaseRules()
                        .validateCategoryRules()
                        .validateOrThrow());

        assertTrue(ex.getFieldErrors().containsKey("referenceNumber"));
        assertTrue(ex.getFieldErrors().containsKey("amount"));
        assertTrue(ex.getFieldErrors().containsKey("metadata.institution"));
        assertTrue(ex.getFieldErrors().containsKey("metadata.billType"));
    }

    @Test
    void shouldPassWhenBaseAndCategoryRulesAreSatisfied() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-200");
        request.setLookupRequired(false);
        request.setAmount(BigDecimal.valueOf(1500));
        request.setMetadata("{\"institution\":\"KRA\",\"billType\":\"TAX\"}");

        assertDoesNotThrow(() -> UtilityPaymentValidationBuilder.forRequest(request)
                .withBillCategory(BillCategory.GOVERNMENT)
                .normalizeOptionalMetadata()
                .validateBaseRules()
                .validateCategoryRules()
                .validateOrThrow());
    }
}