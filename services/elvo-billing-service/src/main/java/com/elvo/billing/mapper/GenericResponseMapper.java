package com.elvo.billing.mapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GenericResponseMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentResponseDto toPaymentResponse(Map<String, Object> providerResponse) {
        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(parseUuid(providerResponse.get("paymentId")));
        response.setExternalReference(stringValue(providerResponse.get("externalReference")));
        response.setStatus(parsePaymentStatus(providerResponse.get("status")));
        response.setMessage(stringValue(providerResponse.get("message")));
        response.setReceiptNumber(stringValue(providerResponse.get("receiptNumber")));
        response.setPaidAmount(parseAmount(providerResponse.get("paidAmount")));
        response.setCurrency(stringValue(providerResponse.get("currency")));
        response.setCompletedAt(parseInstant(providerResponse.get("completedAt")));
        response.setMetadata(normalizeJson(providerResponse.get("metadata"), Map.of()));
        return response;
    }

    public LookupResponseDto toLookupResponse(Map<String, Object> providerResponse) {
        LookupResponseDto response = new LookupResponseDto();
        response.setLookupStatus(parseLookupStatus(providerResponse.get("lookupStatus")));
        response.setCustomerName(stringValue(providerResponse.get("customerName")));
        response.setReferenceNumber(stringValue(providerResponse.get("referenceNumber")));
        response.setAmount(parseAmount(providerResponse.get("amount")));
        response.setCurrency(stringValue(providerResponse.get("currency")));
        response.setDescription(stringValue(providerResponse.get("description")));
        response.setBillItems(normalizeJson(providerResponse.get("billItems"), "[]"));
        response.setRawProviderReference(stringValue(providerResponse.get("rawProviderReference")));
        return response;
    }

    private static UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }

    private static PaymentStatus parsePaymentStatus(Object value) {
        if (value == null) {
            return null;
        }
        return PaymentStatus.valueOf(value.toString().trim().toUpperCase());
    }

    private static LookupStatus parseLookupStatus(Object value) {
        if (value == null) {
            return null;
        }
        return LookupStatus.valueOf(value.toString().trim().toUpperCase());
    }

    private static BigDecimal parseAmount(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString().trim());
    }

    private static Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return Instant.parse(value.toString().trim());
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static String normalizeJson(Object value, Object fallback) {
        if (value == null) {
            return toJson(fallback);
        }

        if (value instanceof String text) {
            if (text.isBlank()) {
                return toJson(fallback);
            }
            if (looksLikeJson(text)) {
                return normalizeJsonString(text, fallback);
            }
            return text;
        }

        return toJson(value);
    }

    private static boolean looksLikeJson(String value) {
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private static String normalizeJsonString(String value, Object fallback) {
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            return toJson(parsed);
        } catch (IOException ex) {
            return toJson(fallback);
        }
    }

    private static String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("provider response contains unsupported payload", ex);
        }
    }
}