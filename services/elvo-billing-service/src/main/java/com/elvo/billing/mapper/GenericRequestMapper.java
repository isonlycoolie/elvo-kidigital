package com.elvo.billing.mapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GenericRequestMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> toProviderRequest(UtilityPaymentRequestDto paymentRequest) {
        Map<String, Object> providerRequest = new LinkedHashMap<>();

        providerRequest.put("referenceNumber", paymentRequest.getReferenceNumber());
        putIfPresent(providerRequest, "amount", paymentRequest.getAmount());
        putIfPresent(providerRequest, "customerPhone", paymentRequest.getCustomerPhone());
        putIfPresent(providerRequest, "customerName", paymentRequest.getCustomerName());
        providerRequest.put("lookupRequired", paymentRequest.isLookupRequired());
        putIfPresent(providerRequest, "idempotencyKey", firstNonBlank(MDC.get("idempotencyKey")));
        providerRequest.put("metadata", parseMetadata(paymentRequest.getMetadata()));

        return providerRequest;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String firstNonBlank(String value) {
        if (value == null || value.isBlank() || "n/a".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private static Map<String, Object> parseMetadata(String metadata) {
        try {
            if (metadata == null || metadata.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            throw new IllegalArgumentException("metadata must contain valid JSON", ex);
        }
    }
}