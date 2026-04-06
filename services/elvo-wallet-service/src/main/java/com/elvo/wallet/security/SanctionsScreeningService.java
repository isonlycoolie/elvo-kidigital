package com.elvo.wallet.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.elvo.wallet.client.IdentityClientProperties;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class SanctionsScreeningService {

    public record ScreeningDecision(boolean allowed, String reason) {
        public static ScreeningDecision allow() {
            return new ScreeningDecision(true, null);
        }

        public static ScreeningDecision block(String reason) {
            return new ScreeningDecision(false, reason);
        }
    }

    private static final String DATA_KEY = "data";

    private final RestTemplate restTemplate;
    private final IdentityClientProperties identityClientProperties;
    private final InternalServiceJwtProperties internalJwtProperties;
    private final String internalJwtSecret;
    private final boolean enabled;
    private final boolean failClosed;
    private final Duration refreshInterval;
    private final Duration maxStaleness;

    private volatile Snapshot snapshot = Snapshot.empty();

    @Autowired
    public SanctionsScreeningService(RestTemplateBuilder restTemplateBuilder,
                                     IdentityClientProperties identityClientProperties,
                                     InternalServiceJwtProperties internalJwtProperties,
                                     SecretManagerService secretManagerService,
                                     @Value("${elvo.security.sanctions.enabled:true}") boolean enabled,
                                     @Value("${elvo.security.sanctions.fail-closed:true}") boolean failClosed,
                                     @Value("${elvo.security.sanctions.refresh-interval-seconds:300}") long refreshIntervalSeconds,
                                     @Value("${elvo.security.sanctions.max-staleness-seconds:1800}") long maxStalenessSeconds) {
        this(restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(identityClientProperties.getConnectTimeoutSeconds()))
                        .setReadTimeout(Duration.ofSeconds(identityClientProperties.getReadTimeoutSeconds()))
                        .build(),
                identityClientProperties,
                internalJwtProperties,
                secretManagerService.resolve(
                        "wallet-internal-jwt-secret",
                        internalJwtProperties.getSecret(),
                        "ELVO_INTERNAL_JWT_SECRET",
                        null),
                enabled,
                failClosed,
                refreshIntervalSeconds,
                maxStalenessSeconds);
    }

    SanctionsScreeningService(RestTemplate restTemplate,
                              IdentityClientProperties identityClientProperties,
                              InternalServiceJwtProperties internalJwtProperties,
                              String internalJwtSecret,
                              boolean enabled,
                              boolean failClosed,
                              long refreshIntervalSeconds,
                              long maxStalenessSeconds) {
        this.restTemplate = restTemplate;
        this.identityClientProperties = identityClientProperties;
        this.internalJwtProperties = internalJwtProperties;
        this.internalJwtSecret = internalJwtSecret;
        this.enabled = enabled;
        this.failClosed = failClosed;
        this.refreshInterval = Duration.ofSeconds(Math.max(30, refreshIntervalSeconds));
        this.maxStaleness = Duration.ofSeconds(Math.max(60, maxStalenessSeconds));
    }

    public ScreeningDecision evaluate(UUID userId, String target) {
        if (!enabled) {
            return ScreeningDecision.allow();
        }

        refreshIfRequired();

        Snapshot current = snapshot;
        if (isStale(current.refreshedAt()) && failClosed) {
            return ScreeningDecision.block("Sanctions refresh unavailable; request blocked by policy");
        }

        if (userId != null && current.sanctionedUserIds().contains(userId.toString())) {
            return ScreeningDecision.block("User is sanctioned and cannot perform this operation");
        }

        String normalizedTarget = normalize(target);
        if (normalizedTarget != null && current.blacklistedTargets().contains(normalizedTarget)) {
            return ScreeningDecision.block("Destination is blacklisted by compliance policy");
        }

        return ScreeningDecision.allow();
    }

    private void refreshIfRequired() {
        Snapshot current = snapshot;
        if (!isRefreshDue(current.refreshedAt())) {
            return;
        }

        synchronized (this) {
            Snapshot reloaded = snapshot;
            if (!isRefreshDue(reloaded.refreshedAt())) {
                return;
            }

            try {
                snapshot = loadSnapshot();
            } catch (RuntimeException ex) {
                snapshot = reloaded.markFailure();
            }
        }
    }

    private Snapshot loadSnapshot() {
        ResponseEntity<Map> response = restTemplate.exchange(
                identityClientProperties.getBaseUrl() + "/compliance/sanctions-blacklist",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                Map.class);

        Map<?, ?> body = response.getBody();
        if (body == null) {
            return Snapshot.empty().markFailure();
        }

        Object dataObj = body.get(DATA_KEY);
        if (!(dataObj instanceof Map<?, ?> data)) {
            return Snapshot.empty().markFailure();
        }

        Set<String> sanctionedUserIds = normalizeSet(readSet(data, "sanctionedUserIds", "sanctionedUsers", "userIds"));
        Set<String> blacklistedTargets = normalizeSet(readSet(data, "blacklistedTargets", "blacklistedNumbers", "targets"));
        return new Snapshot(sanctionedUserIds, blacklistedTargets, Instant.now(), false);
    }

    private Set<String> readSet(Map<?, ?> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof List<?> list) {
                Set<String> results = new HashSet<>();
                for (Object item : list) {
                    if (item != null) {
                        results.add(String.valueOf(item));
                    }
                }
                return results;
            }
        }
        return Collections.emptySet();
    }

    private Set<String> normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String normalizedValue = normalize(value);
            if (normalizedValue != null) {
                normalized.add(normalizedValue);
            }
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(generateInternalServiceToken());
        headers.set("X-Source-Service", identityClientProperties.getSourceServiceName());
        return headers;
    }

    private String generateInternalServiceToken() {
        if (internalJwtSecret == null || internalJwtSecret.isBlank()) {
            throw new IllegalStateException("elvo.security.internal-jwt.secret must be configured");
        }

        SecretKey key = Keys.hmacShaKeyFor(internalJwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(internalJwtProperties.getIssuer())
                .audience().add(internalJwtProperties.getAudience()).and()
                .subject(identityClientProperties.getSourceServiceName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(identityClientProperties.getTokenTtlSeconds())))
                .claim(internalJwtProperties.getSourceServiceClaim(), identityClientProperties.getSourceServiceName())
                .claim(internalJwtProperties.getServiceIdentityClaim(), identityClientProperties.getSourceServiceName())
                .claim("roles", List.of(internalJwtProperties.getRequiredRole()))
                .signWith(key)
                .compact();
    }

    private boolean isRefreshDue(Instant refreshedAt) {
        return Duration.between(refreshedAt, Instant.now()).compareTo(refreshInterval) >= 0;
    }

    private boolean isStale(Instant refreshedAt) {
        return Duration.between(refreshedAt, Instant.now()).compareTo(maxStaleness) > 0;
    }

    private record Snapshot(Set<String> sanctionedUserIds,
                            Set<String> blacklistedTargets,
                            Instant refreshedAt,
                            boolean lastRefreshFailed) {

        private static Snapshot empty() {
            return new Snapshot(Collections.emptySet(), Collections.emptySet(), Instant.EPOCH, true);
        }

        private Snapshot markFailure() {
            return new Snapshot(sanctionedUserIds, blacklistedTargets, refreshedAt, true);
        }
    }
}
