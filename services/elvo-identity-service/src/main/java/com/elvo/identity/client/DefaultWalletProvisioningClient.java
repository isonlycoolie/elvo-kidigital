package com.elvo.identity.client;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DefaultWalletProvisioningClient implements WalletProvisioningClient {

    private final RestClient restClient;
    private final ProvisioningClientProperties properties;
    private final ProvisioningRetryExecutor retryExecutor;
    private final InternalServiceJwtTokenGenerator tokenGenerator;

    public DefaultWalletProvisioningClient(ProvisioningClientProperties properties,
                                           ProvisioningRetryExecutor retryExecutor,
                                           InternalServiceJwtTokenGenerator tokenGenerator) {
        this.properties = properties;
        this.retryExecutor = retryExecutor;
        this.tokenGenerator = tokenGenerator;
        this.restClient = createRestClient(properties.getWalletBaseUrl());
    }

    @Override
    public void createWallet(UUID userId, String idempotencyKey) {
        retryExecutor.execute("wallet provisioning", () -> {
            restClient.post()
                    .uri("/api/v1/internal/wallets/{userId}", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Source-Service", properties.getSourceServiceName())
                    .header("Authorization", "Bearer " + tokenGenerator.generateToken(properties.getSourceServiceName()))
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
        });
    }

    private RestClient createRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1, properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.max(1, properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
