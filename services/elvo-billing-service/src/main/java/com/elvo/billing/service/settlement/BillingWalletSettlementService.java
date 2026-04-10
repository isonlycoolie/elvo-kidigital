package com.elvo.billing.service.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.elvo.billing.exception.PaymentValidationException;

@Service
public class BillingWalletSettlementService {

    private static final String SOURCE_SERVICE_HEADER = "X-Source-Service";

    private final RestClient restClient;
    private final String internalAuthToken;
    private final String sourceServiceName;

    public BillingWalletSettlementService(
            @Value("${elvo.billing.wallet.base-url:http://localhost:8083}") String walletBaseUrl,
            @Value("${elvo.billing.wallet.internal-auth-token:}") String internalAuthToken,
            @Value("${elvo.billing.wallet.source-service:billing-service}") String sourceServiceName) {
        this.restClient = RestClient.builder().baseUrl(walletBaseUrl).build();
        this.internalAuthToken = internalAuthToken;
        this.sourceServiceName = sourceServiceName;
    }

    public WalletReservation reserve(UUID userId, BigDecimal amount, String idempotencyKey) {
        Map<String, Object> payload = Map.of(
                "amount", amount,
                "expiryDate", Instant.now().plus(10, ChronoUnit.MINUTES).toString(),
                "idempotencyKey", idempotencyKey);

        WalletFlowResult response = call(
            restClient.post()
                .uri("/api/v1/internal/wallets/{userId}/reserve", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(SOURCE_SERVICE_HEADER, sourceServiceName)
                .header("Authorization", buildAuthorizationHeader()),
                payload,
                WalletFlowResult.class);

        if (response == null || !response.success || response.transactionId == null || response.walletId == null) {
            throw new PaymentValidationException(response == null ? "Wallet reserve failed" : response.message);
        }

        return new WalletReservation(response.walletId, response.transactionId);
    }

    public void confirm(UUID userId, UUID reservationId, String idempotencyKey) {
        WalletFlowResult response = call(
            restClient.post()
                .uri("/api/v1/internal/wallets/{userId}/confirm-debit", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(SOURCE_SERVICE_HEADER, sourceServiceName)
                .header("Authorization", buildAuthorizationHeader()),
                Map.of("reservationId", reservationId, "idempotencyKey", idempotencyKey),
                WalletFlowResult.class);

        if (response == null || !response.success) {
            throw new PaymentValidationException(response == null ? "Wallet confirm failed" : response.message);
        }
    }

    public void release(UUID userId, UUID reservationId, String idempotencyKey) {
        WalletFlowResult response = call(
            restClient.post()
                .uri("/api/v1/internal/wallets/{userId}/release", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(SOURCE_SERVICE_HEADER, sourceServiceName)
                .header("Authorization", buildAuthorizationHeader()),
                Map.of("reservationId", reservationId, "idempotencyKey", idempotencyKey),
                WalletFlowResult.class);

        if (response == null || !response.success) {
            throw new PaymentValidationException(response == null ? "Wallet release failed" : response.message);
        }
    }

    private <T> T call(RestClient.RequestBodySpec request, Map<String, Object> body, Class<T> type) {
        return request
                .body(body)
                .retrieve()
                .body(type);
    }

    private String buildAuthorizationHeader() {
        if (!StringUtils.hasText(internalAuthToken)) {
            throw new PaymentValidationException("Billing wallet internal auth token is not configured");
        }
        return "Bearer " + internalAuthToken;
    }

    public record WalletReservation(UUID walletId, UUID reservationId) {
    }

    private static final class WalletFlowResult {
        public boolean success;
        public String message;
        public UUID walletId;
        public UUID transactionId;
    }
}
