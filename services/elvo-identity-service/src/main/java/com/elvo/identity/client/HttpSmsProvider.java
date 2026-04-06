package com.elvo.identity.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpSmsProvider implements SmsProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpSmsProvider.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String apiSecret;
    private final String senderId;
    private final int maxAttempts;

    public HttpSmsProvider(@Value("${SMS_PROVIDER_BASE_URL:http://localhost:8088}") String baseUrl,
                           @Value("${SMS_PROVIDER_API_KEY:}") String apiKey,
                           @Value("${SMS_PROVIDER_SECRET:}") String apiSecret,
                           @Value("${SMS_PROVIDER_SENDER_ID:ELVO}") String senderId,
                           @Value("${elvo.communication.sms.retry-max-attempts:3}") int maxAttempts) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.senderId = senderId;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public void sendSms(String destinationPhone, String message, String requestId) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restClient.post()
                        .uri("/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-KEY", apiKey)
                        .header("X-API-SECRET", apiSecret)
                        .body(Map.of(
                                "senderId", senderId,
                                "to", destinationPhone,
                                "message", message,
                                "requestId", requestId
                        ))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RuntimeException ex) {
                lastError = ex;
                LOGGER.warn("OTP SMS send failed requestId={} destination={} attempt={} reason={}",
                        requestId,
                        maskPhone(destinationPhone),
                        attempt,
                        ex.getClass().getSimpleName());
            }
        }

        throw new IllegalStateException("Unable to send OTP SMS", lastError);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        String suffix = phone.substring(phone.length() - 2);
        return "***" + suffix;
    }
}
