package com.elvo.wallet.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.elvo.wallet.security.InternalServiceJwtProperties;
import com.elvo.wallet.security.SecretManagerService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class DefaultIdentityServiceClient implements IdentityServiceClient {

    private static final String DATA_KEY = "data";
    private static final String ACTIVE_KEY = "active";
    private static final String VERIFIED_KEY = "verified";

    private final RestTemplate restTemplate;
    private final IdentityClientProperties properties;
    private final InternalServiceJwtProperties internalJwtProperties;
    private final String internalJwtSecret;

    @Autowired
    public DefaultIdentityServiceClient(RestTemplateBuilder restTemplateBuilder,
                                        IdentityClientProperties properties,
                                        InternalServiceJwtProperties internalJwtProperties,
                                        SecretManagerService secretManagerService) {
        this(restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build(),
            properties,
            internalJwtProperties,
            resolveInternalJwtSecret(secretManagerService, internalJwtProperties.getSecret()));
    }

    DefaultIdentityServiceClient(RestTemplate restTemplate,
                                 IdentityClientProperties properties,
                                 InternalServiceJwtProperties internalJwtProperties) {
        this(restTemplate, properties, internalJwtProperties, internalJwtProperties.getSecret());
    }

    private DefaultIdentityServiceClient(RestTemplate restTemplate,
                                         IdentityClientProperties properties,
                                         InternalServiceJwtProperties internalJwtProperties,
                                         String internalJwtSecret) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.internalJwtProperties = internalJwtProperties;
        this.internalJwtSecret = internalJwtSecret;
    }

    @Override
    public boolean isUserActive(UUID userId) {
        if (userId == null) {
            return false;
        }

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/users/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class);
            return readBooleanDataValue(response.getBody(), ACTIVE_KEY);
        } catch (RestClientException ex) {
            return false;
        }
    }

    @Override
    public boolean verifyEsp(UUID userId, String espCode) {
        if (userId == null || espCode == null || espCode.isBlank()) {
            return false;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "userId", userId,
                    "espCode", espCode,
                    "sourceIp", properties.getClientSourceIp(),
                    "sourceUserAgent", properties.getClientSourceUserAgent());
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/verify-esp",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, buildHeaders()),
                    Map.class);
            return readBooleanDataValue(response.getBody(), VERIFIED_KEY);
        } catch (RestClientException ex) {
            return false;
        }
    }

    @Override
    public boolean verifyEac(UUID userId, String eacCode) {
        if (userId == null || eacCode == null || eacCode.isBlank()) {
            return false;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "userId", userId,
                    "eacCode", eacCode,
                    "sourceIp", properties.getClientSourceIp(),
                    "sourceUserAgent", properties.getClientSourceUserAgent());
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/verify-eac",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, buildHeaders()),
                    Map.class);
            return readBooleanDataValue(response.getBody(), VERIFIED_KEY);
        } catch (RestClientException ex) {
            return false;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(generateInternalServiceToken());
        headers.set("X-Source-Service", properties.getSourceServiceName());
        return headers;
    }

    private String generateInternalServiceToken() {
        String secret = internalJwtSecret;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("elvo.security.internal-jwt.secret must be configured");
        }

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(internalJwtProperties.getIssuer())
                .audience().add(internalJwtProperties.getAudience()).and()
                .subject(properties.getSourceServiceName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getTokenTtlSeconds())))
                .claim(internalJwtProperties.getSourceServiceClaim(), properties.getSourceServiceName())
                .claim(internalJwtProperties.getServiceIdentityClaim(), properties.getSourceServiceName())
                .claim("roles", List.of(internalJwtProperties.getRequiredRole()))
                .signWith(key)
                .compact();
    }

    private boolean readBooleanDataValue(Map<?, ?> responseBody, String key) {
        if (responseBody == null) {
            return false;
        }
        Object dataObj = responseBody.get(DATA_KEY);
        if (!(dataObj instanceof Map<?, ?> data)) {
            return false;
        }
        Object value = data.get(key);
        return Boolean.TRUE.equals(value);
    }

    private static String resolveInternalJwtSecret(SecretManagerService secretManagerService, String configuredSecret) {
        return secretManagerService.resolve(
                "wallet-internal-jwt-secret",
                configuredSecret,
                "ELVO_INTERNAL_JWT_SECRET",
                null);
    }
}
