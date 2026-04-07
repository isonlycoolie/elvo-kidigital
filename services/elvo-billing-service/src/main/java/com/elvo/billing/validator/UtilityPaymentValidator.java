package com.elvo.billing.validator;

import org.springframework.stereotype.Component;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.exception.PaymentValidationException;
import com.elvo.billing.validation.UtilityPaymentValidationBuilder;

/**
 * Validates utility payment requests using the fluent validation builder.
 */
@Component
public class UtilityPaymentValidator {

    /**
     * Validate a payment request for execution.
     * Enforces: non-null referenceNumber, serviceCode, amount, and idempotencyKey.
     * 
     * @param request the request DTO
     * @throws PaymentValidationException if validation fails
     */
    public void validatePaymentRequest(UtilityPaymentRequestDto request) {
        try {
            UtilityPaymentValidationBuilder.forRequest(request)
                    .normalizeOptionalMetadata()
                    .validateBaseRules()
                    .validateCategoryRules()
                    .build();
        } catch (PaymentValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentValidationException("Payment validation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Validate a lookup request (requires serviceCode and referenceNumber).
     * 
     * @param request the lookup request DTO
     * @throws PaymentValidationException if validation fails
     */
    public void validateLookupRequest(UtilityPaymentRequestDto request) {
        if (request == null) {
            throw new PaymentValidationException("Lookup request must not be null");
        }
        if (request.getServiceCode() == null || request.getServiceCode().isBlank()) {
            throw new PaymentValidationException("Lookup request must include serviceCode");
        }
        if (request.getReferenceNumber() == null || request.getReferenceNumber().isBlank()) {
            throw new PaymentValidationException("Lookup request must include referenceNumber");
        }
    }
}
