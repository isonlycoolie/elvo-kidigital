package com.elvo.billing.client;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SelcomHttpClient {

    private final RestClient restClient;

    public SelcomHttpClient() {
        this.restClient = RestClient.create();
    }

    public Map<String, Object> post(String baseUrl, String path, String apiKey, String secret, Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "SELCOM " + apiKey)
                    .header("X-Selcom-Secret", secret)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (RestClientException ex) {
            throw new SelcomProviderException("Selcom HTTP call failed: " + ex.getMessage(), ex);
        }
    }

    public static class SelcomProviderException extends RuntimeException {
        public SelcomProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
