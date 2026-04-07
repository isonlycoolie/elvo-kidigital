package com.elvo.billing.validator;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.validation.UtilityPaymentValidationBuilder;
import org.springframework.stereotype.Component;

@Component
public class UtilityPaymentValidator {

    public void validateForPayment(UtilityPaymentRequestDto request, BillCategory billCategory) {
        UtilityPaymentValidationBuilder.forRequest(request)
                .withBillCategory(billCategory)
                .normalizeOptionalMetadata()
                .validateBaseRules()
                .validateCategoryRules()
                .validateOrThrow();
    }

    public void validateForLookup(UtilityPaymentRequestDto request, BillCategory billCategory) {
        UtilityPaymentValidationBuilder.forRequest(request)
                .withBillCategory(billCategory)
                .normalizeOptionalMetadata()
                .validateBaseRules()
                .validateCategoryRules()
                .validateOrThrow();
    }
}
