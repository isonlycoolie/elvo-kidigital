package com.elvo.wallet.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final String KYC_REVERIFICATION_REQUIRED_KEY = "reverificationRequired";
    private static final String KYC_REVERIFICATION_REQUIRED_ALT_KEY = "reVerificationRequired";
    private static final String KYC_DOCUMENT_EXPIRED_KEY = "documentExpired";
    private static final String KYC_DOCUMENT_EXPIRY_AT_KEY = "documentExpiryAt";
    private static final String KYC_DOCUMENT_EXPIRES_AT_KEY = "documentExpiresAt";

    private final RestTemplate restTemplate;
    private final IdentityClientProperties properties;
    private final InternalServiceJwtProperties internalJwtProperties;
    private final InternalTlsProperties internalTlsProperties;
    private final String internalJwtSecret;

    @Autowired
    public DefaultIdentityServiceClient(RestTemplateBuilder restTemplateBuilder,
                                        IdentityClientProperties properties,
                                        InternalServiceJwtProperties internalJwtProperties,
                                        InternalTlsProperties internalTlsProperties,
                                        SecretManagerService secretManagerService) {
        this(restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                        .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                        .additionalInterceptors((request, body, execution) -> {
                            var response = execution.execute(request, body);
                            validatePinnedCertificate(response.getHeaders(), internalTlsProperties);
                            return response;
                        })
                        .build(),
            properties,
            internalJwtProperties,
            internalTlsProperties,
            resolveInternalJwtSecret(secretManagerService, internalJwtProperties.getSecret()));
    }

    DefaultIdentityServiceClient(RestTemplate restTemplate,
                                 IdentityClientProperties properties,
                                 InternalServiceJwtProperties internalJwtProperties) {
        this(restTemplate, properties, internalJwtProperties, new InternalTlsProperties(), internalJwtProperties.getSecret());
    }

    DefaultIdentityServiceClient(RestTemplate restTemplate,
                                 IdentityClientProperties properties,
                                 InternalServiceJwtProperties internalJwtProperties,
                                 InternalTlsProperties internalTlsProperties) {
        this(restTemplate, properties, internalJwtProperties, internalTlsProperties, internalJwtProperties.getSecret());
    }

    private DefaultIdentityServiceClient(RestTemplate restTemplate,
                                         IdentityClientProperties properties,
                                         InternalServiceJwtProperties internalJwtProperties,
                                         InternalTlsProperties internalTlsProperties,
                                         String internalJwtSecret) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.internalJwtProperties = internalJwtProperties;
        this.internalTlsProperties = internalTlsProperties;
        this.internalJwtSecret = internalJwtSecret;
    }

    @Override
    public boolean isUserActive(UUID userId) {
        if (userId == null) {
            return false;
        }
        if (!isInternalTransportCompliant()) {
            return false;
        }

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/users/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class);
            if (!readBooleanDataValue(response.getBody(), ACTIVE_KEY)) {
                return false;
            }
            return isKycCompliant(userId);
        } catch (RestClientException ex) {
            return false;
        }
    }

    @Override
    public boolean verifyEsp(UUID userId, String espCode) {
        if (userId == null || espCode == null || espCode.isBlank()) {
            return false;
        }
        if (!isInternalTransportCompliant()) {
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
        if (!isInternalTransportCompliant()) {
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

    private boolean isKycCompliant(UUID userId) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/users/" + userId + "/kyc-status",
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class);
            return readKycCompliance(response.getBody());
        } catch (RestClientException ex) {
            return false;
        }
    }

    private boolean readKycCompliance(Map<?, ?> responseBody) {
        if (responseBody == null) {
            return false;
        }
        Object dataObj = responseBody.get(DATA_KEY);
        if (!(dataObj instanceof Map<?, ?> data)) {
            return false;
        }

        if (!Boolean.TRUE.equals(data.get(VERIFIED_KEY))) {
            return false;
        }

        if (Boolean.TRUE.equals(data.get(KYC_REVERIFICATION_REQUIRED_KEY))
                || Boolean.TRUE.equals(data.get(KYC_REVERIFICATION_REQUIRED_ALT_KEY))) {
            return false;
        }

        if (Boolean.TRUE.equals(data.get(KYC_DOCUMENT_EXPIRED_KEY))) {
            return false;
        }

        Instant expiryAt = parseExpiryAt(data);
        if (expiryAt == null) {
            return true;
        }

        Instant now = Instant.now();
        if (!expiryAt.isAfter(now)) {
            return false;
        }

        long reverifyWindowDays = Math.max(1, properties.getKycReverificationWindowDays());
        return expiryAt.isAfter(now.plus(Duration.ofDays(reverifyWindowDays)));
    }

    private Instant parseExpiryAt(Map<?, ?> data) {
        Object rawValue = data.get(KYC_DOCUMENT_EXPIRY_AT_KEY);
        if (rawValue == null) {
            rawValue = data.get(KYC_DOCUMENT_EXPIRES_AT_KEY);
        }
        if (!(rawValue instanceof String expiryText) || expiryText.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(expiryText);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String resolveInternalJwtSecret(SecretManagerService secretManagerService, String configuredSecret) {
        return secretManagerService.resolve(
                "wallet-internal-jwt-secret",
                configuredSecret,
                "ELVO_INTERNAL_JWT_SECRET",
                null);
    }

    private boolean isInternalTransportCompliant() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }

        if (internalTlsProperties != null
                && (internalTlsProperties.isEnforceHttps() || internalTlsProperties.isEnforceMtls())
                && !baseUrl.toLowerCase().startsWith("https://")) {
            return false;
        }
        return true;
    }

    private static void validatePinnedCertificate(HttpHeaders responseHeaders, InternalTlsProperties tlsProperties) {
        if (tlsProperties == null || !tlsProperties.hasPinnedFingerprints()) {
            return;
        }

        String headerName = tlsProperties.getFingerprintHeader();
        String observed = responseHeaders.getFirst(headerName);
        if (observed == null || observed.isBlank()) {
            throw new RestClientException("Missing required TLS fingerprint header: " + headerName);
        }

        String normalizedObserved = normalizeFingerprint(observed);
        boolean matched = tlsProperties.getPinnedFingerprints().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(DefaultIdentityServiceClient::normalizeFingerprint)
                .collect(Collectors.toSet())
                .contains(normalizedObserved);
        if (!matched) {
            throw new RestClientException("TLS certificate pin validation failed");
        }
    }

    private static String normalizeFingerprint(String fingerprint) {
        return fingerprint.replace(":", "").trim().toLowerCase();
    }
}
