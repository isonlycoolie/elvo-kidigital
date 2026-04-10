package com.elvo.identity.client;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class DefaultAccountReadClient implements AccountReadClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAccountReadClient.class);

    private final RestClient restClient;
    private final AccountReadClientProperties properties;

    public DefaultAccountReadClient(AccountReadClientProperties properties) {
        this.properties = properties;
        this.restClient = createRestClient(properties.getBaseUrl());
    }

    @Override
    public Optional<String> findEanByUserId(UUID userId) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/api/v1/internal/accounts/user/{userId}", userId)
                    .header("X-Source-Service", properties.getSourceServiceName());
            if (StringUtils.hasText(properties.getInternalAuthToken())) {
                request = request.header("X-Internal-Auth-Token", properties.getInternalAuthToken());
            }

            ApiResponse<AccountData> response = request.retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {
            });

            if (response == null || response.data() == null || !StringUtils.hasText(response.data().ean())) {
                return Optional.empty();
            }
            return Optional.of(response.data().ean());
        } catch (Exception ex) {
            LOGGER.debug("Account read lookup failed for userId={}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
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

    private record ApiResponse<T>(boolean success, String message, T data) {
    }

    private record AccountData(UUID accountId, UUID userId, String ean) {
    }
}
