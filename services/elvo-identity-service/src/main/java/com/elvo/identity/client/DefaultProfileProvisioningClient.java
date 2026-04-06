package com.elvo.identity.client;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DefaultProfileProvisioningClient implements ProfileProvisioningClient {

    private final RestClient restClient;
    private final ProvisioningClientProperties properties;

    public DefaultProfileProvisioningClient(ProvisioningClientProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.getProfileBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public void createProfile(UUID userId, String idempotencyKey) {
        restClient.post()
                .uri("/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Source-Service", properties.getSourceServiceName())
                .body(Map.of("userId", userId))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void createDefaultPreferences(UUID userId, String idempotencyKey) {
        restClient.post()
                .uri("/profiles/preferences/default")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Source-Service", properties.getSourceServiceName())
                .body(Map.of("userId", userId))
                .retrieve()
                .toBodilessEntity();
    }
}
