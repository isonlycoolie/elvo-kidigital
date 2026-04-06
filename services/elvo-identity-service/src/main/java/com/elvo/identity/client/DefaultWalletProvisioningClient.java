package com.elvo.identity.client;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DefaultWalletProvisioningClient implements WalletProvisioningClient {

    private final RestClient restClient;
    private final ProvisioningClientProperties properties;

    public DefaultWalletProvisioningClient(ProvisioningClientProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.getWalletBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public void createWallet(UUID userId, String idempotencyKey) {
        restClient.post()
                .uri("/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Source-Service", properties.getSourceServiceName())
                .body(Map.of("userId", userId))
                .retrieve()
                .toBodilessEntity();
    }
}
