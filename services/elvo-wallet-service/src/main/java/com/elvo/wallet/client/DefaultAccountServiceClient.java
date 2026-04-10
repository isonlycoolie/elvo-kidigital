package com.elvo.wallet.client;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
public class DefaultAccountServiceClient implements AccountServiceClient {

    private final RestTemplate restTemplate;
    private final AccountClientProperties properties;
    private final InternalServiceJwtProperties internalJwtProperties;
    private final String internalJwtSecret;

    @Autowired
    public DefaultAccountServiceClient(RestTemplateBuilder restTemplateBuilder,
                                       AccountClientProperties properties,
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

    DefaultAccountServiceClient(RestTemplate restTemplate,
                                AccountClientProperties properties,
                                InternalServiceJwtProperties internalJwtProperties) {
        this(restTemplate, properties, internalJwtProperties, internalJwtProperties.getSecret());
    }

    private DefaultAccountServiceClient(RestTemplate restTemplate,
                                        AccountClientProperties properties,
                                        InternalServiceJwtProperties internalJwtProperties,
                                        String internalJwtSecret) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.internalJwtProperties = internalJwtProperties;
        this.internalJwtSecret = internalJwtSecret;
    }

    @Override
    public Optional<AccountSummary> findAccountByUserId(UUID userId) {
        return fetchAccount("/user/{userId}", userId);
    }

    @Override
    public Optional<AccountSummary> findAccountByEan(String ean) {
        if (ean == null || ean.isBlank()) {
            return Optional.empty();
        }
        return fetchAccount("/ean/{ean}", ean);
    }

    @Override
    public AccountValidationResult validateTransfer(AccountValidationRequest request) {
        return validate("/validate-transfer", request);
    }

    @Override
    public AccountValidationResult validateWithdrawal(AccountValidationRequest request) {
        return validate("/validate-withdrawal", request);
    }

    @Override
    public AccountValidationResult validateReceive(AccountValidationRequest request) {
        return validate("/validate-receive", request);
    }

    @Override
    public AccountLimitCheckResult checkLimit(AccountLimitCheckRequest request) {
        if (request == null) {
            return new AccountLimitCheckResult(false, "Request is required", null, null, null);
        }

        try {
            ResponseEntity<ApiResponse<AccountLimitCheckResult>> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/check-limit",
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    new org.springframework.core.ParameterizedTypeReference<>() {
                    });

            return unwrap(response)
                    .map(ApiResponse::data)
                    .orElseGet(() -> new AccountLimitCheckResult(false, "Account service rejected the request", null, null, request.amount()));
        } catch (RestClientException ex) {
            return new AccountLimitCheckResult(false, ex.getMessage(), null, null, request.amount());
        }
    }

    private Optional<AccountSummary> fetchAccount(String path, Object pathValue) {
        if (pathValue == null) {
            return Optional.empty();
        }

        try {
            ResponseEntity<ApiResponse<AccountSummary>> response = restTemplate.exchange(
                    properties.getBaseUrl() + path,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    new org.springframework.core.ParameterizedTypeReference<>() {
                    },
                    pathValue);

            return unwrap(response).map(ApiResponse::data);
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    private AccountValidationResult validate(String path, AccountValidationRequest request) {
        if (request == null) {
            return new AccountValidationResult(false, "Request is required", null, null, null, null);
        }

        try {
            ResponseEntity<ApiResponse<AccountValidationResult>> response = restTemplate.exchange(
                    properties.getBaseUrl() + path,
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    new org.springframework.core.ParameterizedTypeReference<>() {
                    });

                return unwrap(response)
                    .map(ApiResponse::data)
                    .orElseGet(() -> new AccountValidationResult(false, "Account service rejected the request", null, null, null, null));
        } catch (RestClientException ex) {
            return new AccountValidationResult(false, ex.getMessage(), null, null, null, null);
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
        if (internalJwtSecret == null || internalJwtSecret.isBlank()) {
            throw new IllegalStateException("elvo.security.internal-jwt.secret must be configured");
        }

        var key = Keys.hmacShaKeyFor(internalJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var now = java.time.Instant.now();
        return Jwts.builder()
                .issuer(internalJwtProperties.getIssuer())
                .audience().add(internalJwtProperties.getAudience()).and()
                .subject(properties.getSourceServiceName())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(properties.getTokenTtlSeconds())))
                .claim(internalJwtProperties.getSourceServiceClaim(), properties.getSourceServiceName())
                .claim(internalJwtProperties.getServiceIdentityClaim(), properties.getSourceServiceName())
                .claim("roles", java.util.List.of(internalJwtProperties.getRequiredRole()))
                .signWith(key)
                .compact();
    }

    private static String resolveInternalJwtSecret(SecretManagerService secretManagerService, String configuredSecret) {
        return secretManagerService.resolve(
                "wallet-internal-jwt-secret",
                configuredSecret,
                "ELVO_INTERNAL_JWT_SECRET",
                null);
    }

    private <T> Optional<ApiResponse<T>> unwrap(ResponseEntity<ApiResponse<T>> response) {
        if (response == null) {
            return Optional.empty();
        }
        ApiResponse<T> body = response.getBody();
        if (body == null || !body.success()) {
            return Optional.empty();
        }
        return Optional.of(body);
    }

    private record ApiResponse<T>(boolean success, String message, T data) {
    }
}