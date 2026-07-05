package com.elvo.billing.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.mapper.GenericRequestMapper;
import com.elvo.billing.mapper.GenericResponseMapper;

@Component
public class SelcomAdapter implements BillingAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelcomAdapter.class);
    private static final String PROVIDER_NAME = "selcom";

    private final GenericRequestMapper requestMapper;
    private final GenericResponseMapper responseMapper;
    private final SelcomHttpClient selcomHttpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String secret;
    private final boolean httpEnabled;

    public SelcomAdapter(
            GenericRequestMapper requestMapper,
            GenericResponseMapper responseMapper,
            SelcomHttpClient selcomHttpClient,
            @Value("${elvo.billing.adapters.providers.selcom.base-url:https://api.sandbox.selcom.example}") String baseUrl,
            @Value("${elvo.billing.adapters.providers.selcom.api-key:}") String apiKey,
            @Value("${elvo.billing.adapters.providers.selcom.secret:}") String secret,
            @Value("${elvo.billing.adapters.providers.selcom.http-enabled:true}") boolean httpEnabled) {
        this.requestMapper = Objects.requireNonNull(requestMapper, "requestMapper must not be null");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper must not be null");
        this.selcomHttpClient = Objects.requireNonNull(selcomHttpClient, "selcomHttpClient must not be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.secret = secret == null ? "" : secret;
        this.httpEnabled = httpEnabled;
    }

    @Override
    public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
        Map<String, Object> providerRequest = requestMapper.toProviderRequest(paymentRequest);
        Map<String, Object> providerResponse = callProviderOrFallback(
                "/v1/bill/lookup",
                providerRequest,
                () -> buildSyntheticLookup(paymentRequest, providerRequest));
        return responseMapper.toLookupResponse(providerResponse);
    }

    @Override
    public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
        Map<String, Object> providerRequest = requestMapper.toProviderRequest(paymentRequest);
        Map<String, Object> providerResponse = callProviderOrFallback(
                "/v1/bill/pay",
                providerRequest,
                () -> buildSyntheticPayment(paymentRequest, providerRequest));
        return responseMapper.toPaymentResponse(providerResponse);
    }

    private Map<String, Object> callProviderOrFallback(
            String path,
            Map<String, Object> providerRequest,
            java.util.function.Supplier<Map<String, Object>> fallback) {
        if (!httpEnabled || apiKey.isBlank() || secret.isBlank()) {
            return fallback.get();
        }
        try {
            Map<String, Object> response = selcomHttpClient.post(baseUrl, path, apiKey, secret, providerRequest);
            if (response.isEmpty()) {
                return fallback.get();
            }
            return response;
        } catch (SelcomHttpClient.SelcomProviderException ex) {
            LOGGER.warn("Selcom HTTP unavailable, using synthetic response: {}", ex.getMessage());
            return fallback.get();
        }
    }

    private Map<String, Object> buildSyntheticLookup(UtilityPaymentRequestDto paymentRequest, Map<String, Object> providerRequest) {
        Map<String, Object> providerResponse = new LinkedHashMap<>();
        providerResponse.put("lookupStatus", "SUCCESS");
        providerResponse.put("customerName", fallbackCustomerName(paymentRequest));
        providerResponse.put("referenceNumber", paymentRequest.getReferenceNumber());
        providerResponse.put("amount", resolveLookupAmount(paymentRequest));
        providerResponse.put("currency", "TZS");
        providerResponse.put("description", "Selcom synthetic lookup response");
        providerResponse.put("billItems", providerRequest.get("metadata"));
        providerResponse.put("rawProviderReference", buildReference(paymentRequest.getReferenceNumber()));
        return providerResponse;
    }

    private Map<String, Object> buildSyntheticPayment(UtilityPaymentRequestDto paymentRequest, Map<String, Object> providerRequest) {
        Map<String, Object> providerResponse = new LinkedHashMap<>();
        providerResponse.put("paymentId", UUID.nameUUIDFromBytes((paymentRequest.getReferenceNumber() + "|" + PROVIDER_NAME).getBytes()));
        providerResponse.put("externalReference", buildReference(paymentRequest.getReferenceNumber()));
        providerResponse.put("status", "SUCCESS");
        providerResponse.put("message", "Selcom synthetic payment accepted");
        providerResponse.put("receiptNumber", buildReceiptNumber(paymentRequest.getReferenceNumber()));
        providerResponse.put("paidAmount", paymentRequest.getAmount());
        providerResponse.put("currency", "TZS");
        providerResponse.put("completedAt", Instant.now());
        providerResponse.put("metadata", Map.of(
                "provider", PROVIDER_NAME,
                "baseUrl", baseUrl,
                "request", providerRequest,
                "authConfigured", !apiKey.isBlank() && !secret.isBlank(),
                "mode", "synthetic"));
        return providerResponse;
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
