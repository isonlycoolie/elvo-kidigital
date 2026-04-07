package com.elvo.billing.client;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component("mockAdapter")
public class MockAdapter implements BillingAdapter {

    private static final String PROVIDER_NAME = "mock";
    private static final Instant COMPLETED_AT = Instant.parse("2026-04-07T00:00:00Z");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
        LookupResponseDto response = new LookupResponseDto();
        response.setLookupStatus(LookupStatus.SUCCESS);
        response.setCustomerName(resolveCustomerName(paymentRequest));
        response.setReferenceNumber(paymentRequest.getReferenceNumber());
        response.setAmount(resolveAmount(paymentRequest));
        response.setCurrency("TZS");
        response.setDescription("Mock lookup response");
        response.setBillItems("[]");
        response.setRawProviderReference(buildReference(paymentRequest.getReferenceNumber()));
        return response;
    }

    @Override
    public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(UUID.nameUUIDFromBytes((PROVIDER_NAME + "|" + paymentRequest.getReferenceNumber()).getBytes(StandardCharsets.UTF_8)));
        response.setExternalReference(buildReference(paymentRequest.getReferenceNumber()));
        response.setStatus(PaymentStatus.SUCCESS);
        response.setMessage("Mock payment completed");
        response.setReceiptNumber(buildReceiptNumber(paymentRequest.getReferenceNumber()));
        response.setPaidAmount(resolveAmount(paymentRequest));
        response.setCurrency("TZS");
        response.setCompletedAt(COMPLETED_AT);
        response.setMetadata(buildMetadata(paymentRequest));
        return response;
    }

    private static BigDecimal resolveAmount(UtilityPaymentRequestDto paymentRequest) {
        if (paymentRequest.getAmount() != null) {
            return paymentRequest.getAmount();
        }
        return BigDecimal.valueOf(Math.abs(paymentRequest.getReferenceNumber().hashCode() % 100_000) / 100.0);
    }

    private static String resolveCustomerName(UtilityPaymentRequestDto paymentRequest) {
        if (paymentRequest.getCustomerName() != null && !paymentRequest.getCustomerName().isBlank()) {
            return paymentRequest.getCustomerName();
        }
        return "Mock Customer";
    }

    private static String buildReference(String referenceNumber) {
        return "MOCK-" + referenceNumber;
    }

    private static String buildReceiptNumber(String referenceNumber) {
        return "MOCK-REC-" + Math.abs(referenceNumber.hashCode());
    }

    private static String buildMetadata(UtilityPaymentRequestDto paymentRequest) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", PROVIDER_NAME);
        metadata.put("mode", "deterministic");
        metadata.put("referenceNumber", paymentRequest.getReferenceNumber());
        metadata.put("lookupRequired", paymentRequest.isLookupRequired());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("unable to serialize mock payment metadata", ex);
        }
    }
}