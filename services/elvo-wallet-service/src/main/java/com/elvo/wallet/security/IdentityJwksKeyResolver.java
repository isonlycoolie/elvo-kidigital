package com.elvo.wallet.security;

import java.net.URI;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.elvo.wallet.client.IdentityClientProperties;

@Service
public class IdentityJwksKeyResolver {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestTemplate restTemplate;
    private final URI jwksUri;
    private volatile CachedJwks cachedJwks = CachedJwks.empty();

    @Autowired
    public IdentityJwksKeyResolver(RestTemplateBuilder restTemplateBuilder, IdentityClientProperties properties) {
        this(restTemplateBuilder.build(), properties);
    }

    IdentityJwksKeyResolver(RestTemplate restTemplate, IdentityClientProperties properties) {
        this.restTemplate = restTemplate;
        this.jwksUri = URI.create(resolveJwksUrl(properties.getBaseUrl()));
    }

    public PublicKey resolve(String keyId) {
        if (!hasText(keyId)) {
            throw new IllegalArgumentException("Token key id is invalid");
        }

        CachedJwks snapshot = cachedJwks;
        PublicKey cachedKey = snapshot.keys().get(keyId);
        if (cachedKey != null && snapshot.isFresh()) {
            return cachedKey;
        }

        synchronized (this) {
            snapshot = cachedJwks;
            cachedKey = snapshot.keys().get(keyId);
            if (cachedKey != null && snapshot.isFresh()) {
                return cachedKey;
            }

            try {
                Map<String, PublicKey> refreshedKeys = fetchKeys();
                cachedJwks = new CachedJwks(refreshedKeys, Instant.now());
                PublicKey refreshedKey = refreshedKeys.get(keyId);
                if (refreshedKey != null) {
                    return refreshedKey;
                }
            } catch (RestClientException ex) {
                if (cachedKey != null) {
                    return cachedKey;
                }
                throw new IllegalStateException("Identity JWKS is unavailable", ex);
            }

            cachedKey = cachedJwks.keys().get(keyId);
            if (cachedKey != null) {
                return cachedKey;
            }

            if (snapshot.keys().containsKey(keyId)) {
                return snapshot.keys().get(keyId);
            }

            throw new IllegalArgumentException("Token key id is invalid");
        }
    }

    private Map<String, PublicKey> fetchKeys() {
        JwksDocument jwksDocument = restTemplate.getForObject(jwksUri, JwksDocument.class);
        if (jwksDocument == null || jwksDocument.keys() == null || jwksDocument.keys().isEmpty()) {
            throw new IllegalStateException("Identity JWKS did not contain any keys");
        }

        Map<String, PublicKey> resolvedKeys = new LinkedHashMap<>();
        for (JwkKey key : jwksDocument.keys()) {
            if (!"RSA".equalsIgnoreCase(key.kty()) || !"sig".equalsIgnoreCase(key.use())) {
                continue;
            }
            resolvedKeys.put(key.kid(), buildRsaPublicKey(key));
        }
        if (resolvedKeys.isEmpty()) {
            throw new IllegalStateException("Identity JWKS did not contain any usable RSA signing keys");
        }
        return resolvedKeys;
    }

    private PublicKey buildRsaPublicKey(JwkKey key) {
        try {
            byte[] modulusBytes = Base64.getUrlDecoder().decode(key.n());
            byte[] exponentBytes = Base64.getUrlDecoder().decode(key.e());
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new java.math.BigInteger(1, modulusBytes),
                    new java.math.BigInteger(1, exponentBytes));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid identity JWKS key material", ex);
        }
    }

    private String resolveJwksUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.isEmpty()) {
            return "https://localhost:8381/.well-known/jwks.json";
        }
        if (normalized.endsWith("/internal")) {
            normalized = normalized.substring(0, normalized.length() - "/internal".length());
        }
        String finalUrl = normalized.replaceAll("/+$", "") + "/.well-known/jwks.json";
        
        // SECURITY: Enforce HTTPS protocol for JWKS endpoint
        if (!finalUrl.startsWith("https://")) {
            throw new IllegalStateException(
                "JWKS endpoint MUST use HTTPS protocol. Received: " + finalUrl + 
                ". Configure HTTPS identity service URL via ELVO_IDENTITY_INTERNAL_BASE_URL environment variable."
            );
        }
        
        return finalUrl;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CachedJwks(Map<String, PublicKey> keys, Instant fetchedAt) {
        static CachedJwks empty() {
            return new CachedJwks(Map.of(), Instant.EPOCH);
        }

        boolean isFresh() {
            return Duration.between(fetchedAt, Instant.now()).compareTo(CACHE_TTL) < 0;
        }
    }

    public record JwksDocument(List<JwkKey> keys) {
    }

    public record JwkKey(String kid, String kty, String alg, String use, String n, String e) {
    }
}