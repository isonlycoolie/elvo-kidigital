package com.elvo.identity.client;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class DefaultProfileProvisioningClient implements ProfileProvisioningClient {

    private final RestClient restClient;
    private final ProvisioningClientProperties properties;
    private final ProvisioningRetryExecutor retryExecutor;

    public DefaultProfileProvisioningClient(ProvisioningClientProperties properties,
                                            ProvisioningRetryExecutor retryExecutor) {
        this.properties = properties;
        this.retryExecutor = retryExecutor;
        this.restClient = createRestClient(properties.getProfileBaseUrl());
    }

    @Override
    public void createProfile(UUID userId, String idempotencyKey) {
        retryExecutor.execute("profile provisioning", () -> {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri("/profiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Source-Service", properties.getSourceServiceName());
            if (StringUtils.hasText(properties.getInternalAuthToken())) {
                request = request.header("X-Internal-Auth-Token", properties.getInternalAuthToken());
            }
            request.body(Map.of("userId", userId))
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

    @Override
    public void createDefaultPreferences(UUID userId, String idempotencyKey) {
        retryExecutor.execute("profile preferences provisioning", () -> {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri("/profiles/preferences/default")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Source-Service", properties.getSourceServiceName());
            if (StringUtils.hasText(properties.getInternalAuthToken())) {
                request = request.header("X-Internal-Auth-Token", properties.getInternalAuthToken());
            }
            request.body(Map.of("userId", userId))
                    .retrieve()
                    .toBodilessEntity();
        });
    }
}
