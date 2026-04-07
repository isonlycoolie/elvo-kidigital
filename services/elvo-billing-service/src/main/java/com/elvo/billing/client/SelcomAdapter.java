package com.elvo.billing.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.mapper.GenericRequestMapper;
import com.elvo.billing.mapper.GenericResponseMapper;

@Component
public class SelcomAdapter implements BillingAdapter {

    private static final String PROVIDER_NAME = "selcom";

    private final GenericRequestMapper requestMapper;
    private final GenericResponseMapper responseMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String secret;

    public SelcomAdapter(
            GenericRequestMapper requestMapper,
            GenericResponseMapper responseMapper,
            @Value("${elvo.billing.adapters.providers.selcom.base-url:https://api.sandbox.selcom.example}") String baseUrl,
            @Value("${elvo.billing.adapters.providers.selcom.api-key:}") String apiKey,
            @Value("${elvo.billing.adapters.providers.selcom.secret:}") String secret) {
        this.requestMapper = Objects.requireNonNull(requestMapper, "requestMapper must not be null");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.secret = Objects.requireNonNull(secret, "secret must not be null");
    }

    @Override
    public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
        Map<String, Object> providerRequest = requestMapper.toProviderRequest(paymentRequest);
        Map<String, Object> providerResponse = new LinkedHashMap<>();
        providerResponse.put("lookupStatus", "SUCCESS");
        providerResponse.put("customerName", fallbackCustomerName(paymentRequest));
        providerResponse.put("referenceNumber", paymentRequest.getReferenceNumber());
        providerResponse.put("amount", resolveLookupAmount(paymentRequest));
        providerResponse.put("currency", "TZS");
        providerResponse.put("description", "Selcom lookup response");
        providerResponse.put("billItems", providerRequest.get("metadata"));
        providerResponse.put("rawProviderReference", buildReference(paymentRequest.getReferenceNumber()));
        return responseMapper.toLookupResponse(providerResponse);
    }

    @Override
    public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
        Map<String, Object> providerRequest = requestMapper.toProviderRequest(paymentRequest);
        Map<String, Object> providerResponse = new LinkedHashMap<>();
        providerResponse.put("paymentId", UUID.nameUUIDFromBytes((paymentRequest.getReferenceNumber() + "|" + PROVIDER_NAME).getBytes()));
        providerResponse.put("externalReference", buildReference(paymentRequest.getReferenceNumber()));
        providerResponse.put("status", "SUCCESS");
        providerResponse.put("message", "Selcom payment accepted");
        providerResponse.put("receiptNumber", buildReceiptNumber(paymentRequest.getReferenceNumber()));
        providerResponse.put("paidAmount", paymentRequest.getAmount());
        providerResponse.put("currency", "TZS");
        providerResponse.put("completedAt", Instant.now());
        providerResponse.put("metadata", Map.of(
                "provider", PROVIDER_NAME,
                "baseUrl", baseUrl,
                "request", providerRequest,
                "authConfigured", !apiKey.isBlank() && !secret.isBlank()));
        return responseMapper.toPaymentResponse(providerResponse);
    }

    private static String buildReference(String referenceNumber) {
        return "SELCOM-" + referenceNumber;
    }

    private static String buildReceiptNumber(String referenceNumber) {
        return "SEL-" + referenceNumber.hashCode();
    }

    private static BigDecimal resolveLookupAmount(UtilityPaymentRequestDto paymentRequest) {
        if (paymentRequest.getAmount() != null) {
            return paymentRequest.getAmount();
        }
        return BigDecimal.valueOf(Math.abs(paymentRequest.getReferenceNumber().hashCode() % 100_000) / 100.0);
    }

    private static String fallbackCustomerName(UtilityPaymentRequestDto paymentRequest) {
        if (paymentRequest.getCustomerName() != null && !paymentRequest.getCustomerName().isBlank()) {
            return paymentRequest.getCustomerName();
        }
        return "Selcom Customer";
    }
}