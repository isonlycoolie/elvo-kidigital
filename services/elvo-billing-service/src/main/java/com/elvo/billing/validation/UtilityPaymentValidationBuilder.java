package com.elvo.billing.validation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.exception.PaymentValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class UtilityPaymentValidationBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final UtilityPaymentRequestDto request;
    private final Map<String, String> fieldErrors = new LinkedHashMap<>();
    private BillCategory billCategory;

    private UtilityPaymentValidationBuilder(UtilityPaymentRequestDto request) {
        this.request = request;
    }

    public static UtilityPaymentValidationBuilder forRequest(UtilityPaymentRequestDto request) {
        return new UtilityPaymentValidationBuilder(request);
    }

    public UtilityPaymentValidationBuilder withBillCategory(BillCategory billCategory) {
        this.billCategory = billCategory;
        return this;
    }

    public UtilityPaymentValidationBuilder normalizeOptionalMetadata() {
        if (request != null) {
            request.setMetadata(request.getMetadata());
        }
        return this;
    }

    public UtilityPaymentValidationBuilder validateBaseRules() {
        if (request == null) {
            fieldErrors.put("request", "request body is required");
            return this;
        }

        if (request.getReferenceNumber() == null || request.getReferenceNumber().isBlank()) {
            fieldErrors.put("referenceNumber", "referenceNumber is required");
        }

        if (!request.isLookupRequired() && request.getAmount() == null) {
            fieldErrors.put("amount", "amount is required when lookupRequired is false");
        }

        return this;
    }

    public UtilityPaymentValidationBuilder validateCategoryRules() {
        if (request == null || billCategory == null) {
            return this;
        }

        Map<String, Object> metadata = parseMetadata(request.getMetadata());

        switch (billCategory) {
            case GOVERNMENT -> {
                requireMetadata(metadata, "institution");
                requireMetadata(metadata, "billType");
            }
            case ELECTRICITY -> requireMetadata(metadata, "meterType");
            case WATER -> requireMetadata(metadata, "institution");
            case TV_SUBSCRIPTION -> requireMetadata(metadata, "packageCode");
            case AIRTIME -> requireMetadata(metadata, "networkCode");
            case INTERNET -> requireMetadata(metadata, "packageCode");
            case HOSPITAL -> requireMetadata(metadata, "hospitalCode");
            case AIRLINE -> requireMetadata(metadata, "bookingCode");
            default -> {
            }
        }

        return this;
    }

    public void validateOrThrow() {
        if (fieldErrors.isEmpty()) {
            return;
        }

        String fields = String.join(", ", fieldErrors.keySet());
        throw new PaymentValidationException("validation failed for fields: " + fields, fieldErrors);
    }

    private static Map<String, Object> parseMetadata(String metadata) {
        try {
            if (metadata == null || metadata.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private void requireMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            fieldErrors.put("metadata." + key, "metadata field '" + key + "' is required for " + billCategory);
        }
    }
}