package com.elvo.wallet.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DefaultAgentServiceClient implements AgentServiceClient {

    private final RestClient restClient;
    private final boolean httpEnabled;

    public DefaultAgentServiceClient(@Value("${elvo.clients.agent.base-url:}") String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.restClient = RestClient.builder().baseUrl(baseUrl).build();
            this.httpEnabled = true;
        } else {
            this.restClient = null;
            this.httpEnabled = false;
        }
    }

    @Override
    public boolean hasAvailableFloat(UUID userId, BigDecimal amount) {
        if (userId == null || amount == null || amount.signum() <= 0) {
            return false;
        }
        if (!httpEnabled) {
            return true;
        }

        FloatCheckResponse response = restClient.post()
                .uri("/api/v1/internal/agents/float/check")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", userId, "amount", amount))
                .retrieve()
                .body(FloatCheckResponse.class);

        return response != null && response.available;
    }

    private static final class FloatCheckResponse {
        public boolean available;
    }
}
