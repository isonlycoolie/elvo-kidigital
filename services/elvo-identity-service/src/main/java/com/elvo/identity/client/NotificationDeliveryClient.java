package com.elvo.identity.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "elvo.communication.notification.enabled", havingValue = "true")
public class NotificationDeliveryClient {

    private final RestClient restClient;
    private final String basicAuthHeader;

    public NotificationDeliveryClient(
            @Value("${elvo.communication.notification.base-url:http://localhost:8085}") String baseUrl,
            @Value("${elvo.communication.notification.username:user}") String username,
            @Value("${elvo.communication.notification.password:}") String password) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.basicAuthHeader = "Basic " + token;
    }

    public void send(String channel, String recipient, String message, String templateCode) {
        restClient.post()
                .uri("/api/v1/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", basicAuthHeader)
                .body(Map.of(
                        "channel", channel,
                        "recipient", recipient,
                        "message", message,
                        "templateCode", templateCode == null ? "" : templateCode))
                .retrieve()
                .toBodilessEntity();
    }
}
