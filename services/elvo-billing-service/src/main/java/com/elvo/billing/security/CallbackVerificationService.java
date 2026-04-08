package com.elvo.billing.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Callback Verification Service
 * Validates provider callbacks using signature verification, source allowlisting,
 * timestamp validation, and replay protection.
 */
@Service
public class CallbackVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(CallbackVerificationService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${elvo.billing.callback.timestamp-threshold-seconds:300}")
    private long timestampThresholdSeconds;

    @Value("${elvo.billing.callback.allowed-sources:}")
    private String allowedSources;

    @Value("${elvo.billing.callback.provider-secrets:}")
    private String providerSecrets;

    private final Map<String, String> providerSecretMap = new HashMap<>();
    private final Set<String> sourceAllowlist = new HashSet<>();

    public CallbackVerificationService() {
        // Secrets and allowlist are populated from environment in validateConfiguration()
    }

    /**
     * Set provider secret for testing or direct initialization.
     */
    public void setProviderSecret(String providerId, String secret) {
        providerSecretMap.put(providerId, secret);
    }

    /**
     * Verify callback signature, source, timestamp, and replay status.
     * Throws CallbackVerificationException if any check fails.
     * 
     * @param payloadJson JSON payload of callback
     * @param signature X-Signature header value (provider-generated)
     * @param nonce X-Nonce header value (unique per callback)
     * @param timestamp X-Timestamp header value (ISO-8601 or epoch millis)
     * @param sourceIp Client IP address from request
     * @param providerId Provider identifier (used to lookup secret)
     * @throws CallbackVerificationException if verification fails
     */
    public void verifyCallback(
            String payloadJson,
            String signature,
            String nonce,
            String timestamp,
            String sourceIp,
            String providerId) throws CallbackVerificationException {

        // Validate required fields
        if (signature == null || signature.isBlank()) {
            throw new CallbackVerificationException("Missing X-Signature header");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new CallbackVerificationException("Missing X-Nonce header");
        }
        if (timestamp == null || timestamp.isBlank()) {
            throw new CallbackVerificationException("Missing X-Timestamp header");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new CallbackVerificationException("Empty callback payload");
        }

        // 1. Verify signature using provider-specific secret
        verifySignature(payloadJson, signature, providerId);

        // 2. Validate timestamp freshness
        validateTimestamp(timestamp);

        // 3. Validate source IP against allowlist
        validateSource(sourceIp);

        // 4. Check nonce for replay protection
        validateNonce(nonce, providerId);

        LOG.info("Callback verified: providerId={} nonce={}", providerId, nonce);
    }

    /**
     * Verify HMAC-SHA256 signature of callback payload.
     */
    private void verifySignature(String payloadJson, String providedSignature, String providerId) throws CallbackVerificationException {
        String secret = resolveProviderSecret(providerId);
        if (secret == null || secret.isBlank()) {
            throw new CallbackVerificationException("No secret configured for provider: " + providerId);
        }

        try {
            String expectedSignature = computeSignature(payloadJson, secret);
            if (!constantTimeEquals(expectedSignature, providedSignature)) {
                LOG.warn("Signature mismatch for provider={}", providerId);
                throw new CallbackVerificationException("Invalid callback signature");
            }
        } catch (Exception e) {
            LOG.error("Signature verification failed", e);
            throw new CallbackVerificationException("Signature verification failed: " + e.getMessage());
        }
    }

    /**
     * Validate timestamp is within acceptable window (default 5 minutes).
     * Protects against replay of old callbacks.
     */
    private void validateTimestamp(String timestamp) throws CallbackVerificationException {
        Instant callbackTime;
        try {
            // Try ISO-8601 format first
            try {
                callbackTime = Instant.parse(timestamp);
            } catch (Exception e) {
                // Fall back to epoch millis
                callbackTime = Instant.ofEpochMilli(Long.parseLong(timestamp));
            }
        } catch (Exception e) {
            throw new CallbackVerificationException("Invalid timestamp format: " + timestamp);
        }

        Instant now = Instant.now();
        long secondsOld = Math.abs(now.getEpochSecond() - callbackTime.getEpochSecond());

        if (secondsOld > timestampThresholdSeconds) {
            throw new CallbackVerificationException(
                    "Timestamp outside acceptable window: " + secondsOld + " seconds old");
        }
    }

    /**
     * Validate source IP against allowlist.
     * If no allowlist is configured, all sources are accepted.
     */
    private void validateSource(String sourceIp) throws CallbackVerificationException {
        if (sourceAllowlist.isEmpty()) {
            // No allowlist configured; accept all sources
            return;
        }

        if (sourceIp == null || sourceIp.isBlank()) {
            throw new CallbackVerificationException("Cannot verify source: client IP unknown");
        }

        if (!sourceAllowlist.contains(sourceIp)) {
            LOG.warn("Callback from untrusted source: {}", sourceIp);
            throw new CallbackVerificationException("Source IP not in allowlist: " + sourceIp);
        }
    }

    /**
     * Track nonce to prevent duplicate callback processing (replay protection).
     * Uses in-memory cache; for production, should use distributed cache (Redis).
     */
    private final Map<String, Long> nonceCache = Collections.synchronizedMap(new LinkedHashMap<String, Long>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            // Evict entries older than threshold
            return size() > 10000 || (System.currentTimeMillis() - (Long) eldest.getValue()) > (timestampThresholdSeconds * 1000);
        }
    });

    private void validateNonce(String nonce, String providerId) throws CallbackVerificationException {
        String nonceKey = providerId + ":" + nonce;
        if (nonceCache.containsKey(nonceKey)) {
            LOG.warn("Duplicate callback detected: nonce={} providerId={}", nonce, providerId);
            throw new CallbackVerificationException("Duplicate callback (nonce already seen)");
        }
        nonceCache.put(nonceKey, System.currentTimeMillis());
    }

    /**
     * Compute HMAC-SHA256 signature of payload.
     */
    private String computeSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        int result = 0;
        for (int i = 0; i < Math.min(aBytes.length, bBytes.length); i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0 && aBytes.length == bBytes.length;
    }

    /**
     * Resolve provider-specific secret from configuration.
     * Format: provider1=secret1,provider2=secret2
     */
    private String resolveProviderSecret(String providerId) {
        if (providerSecretMap.isEmpty() && providerSecrets != null && !providerSecrets.isBlank()) {
            // Lazy-load secrets from config
            for (String entry : providerSecrets.split(",")) {
                String[] parts = entry.trim().split("=");
                if (parts.length == 2) {
                    providerSecretMap.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return providerSecretMap.get(providerId);
    }

    /**
     * Initialize allowlist from configuration.
     * Can be called at startup to load allowed IPs.
     */
    public void initializeAllowlist(List<String> ips) {
        if (ips != null) {
            sourceAllowlist.addAll(ips);
        }
        // Also parse from comma-separated config if present
        if (allowedSources != null && !allowedSources.isBlank()) {
            for (String ip : allowedSources.split(",")) {
                sourceAllowlist.add(ip.trim());
            }
        }
    }

    /**
     * Custom exception for callback verification failures.
     */
    public static class CallbackVerificationException extends Exception {
        public CallbackVerificationException(String message) {
            super(message);
        }
        public CallbackVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
