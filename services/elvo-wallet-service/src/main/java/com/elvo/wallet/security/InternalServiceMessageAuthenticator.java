package com.elvo.wallet.security;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class InternalServiceMessageAuthenticator {

    public static final String SOURCE_SERVICE_FIELD = "sourceService";
    public static final String SERVICE_TOKEN_FIELD = "serviceToken";
    public static final String SIGNATURE_VERSION_FIELD = "signatureVersion";
    public static final String SIGNATURE_VERSION = "hmac-sha256-v1";
    public static final String MESSAGE_ID_FIELD = "messageId";
    public static final String NONCE_FIELD = "nonce";
    public static final String EXPIRES_AT_FIELD = "expiresAt";

    private static final String SECRET_ENV = "ELVO_INTERNAL_SERVICE_SECRET";
    private static final String SECRET_MANAGER_PREFIX = "sm://";
    private static final String SECRET_MANAGER_PROPERTY_PREFIX = "elvo.secret-manager.secrets.";
    private static final String TEST_FALLBACK_SECRET = "elvo-test-internal-service-secret";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration MAX_ACCEPTED_EVENT_AGE = Duration.ofMinutes(5);
    private static final Duration MAX_FUTURE_DRIFT = Duration.ofMinutes(1);

    private static final ConcurrentMap<String, Instant> SEEN_MESSAGE_IDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Instant> SEEN_NONCES = new ConcurrentHashMap<>();

    private InternalServiceMessageAuthenticator() {
    }

    public static Map<String, Object> signEvent(String sourceService, Map<String, Object> event) {
        Objects.requireNonNull(sourceService, "sourceService must not be null");

        Map<String, Object> signedEvent = new LinkedHashMap<>();
        if (event != null) {
            signedEvent.putAll(event);
        }
        signedEvent.put(SOURCE_SERVICE_FIELD, sourceService);
        signedEvent.put(SIGNATURE_VERSION_FIELD, SIGNATURE_VERSION);
        signedEvent.put(SERVICE_TOKEN_FIELD, computeSignature(sourceService, signedEvent));
        return signedEvent;
    }

    public static boolean isTrusted(Map<String, Object> event, String expectedSourceService) {
        if (event == null || expectedSourceService == null || expectedSourceService.isBlank()) {
            return false;
        }

        String sourceService = stringValue(event.get(SOURCE_SERVICE_FIELD));
        String token = stringValue(event.get(SERVICE_TOKEN_FIELD));
        if (sourceService == null || token == null) {
            return false;
        }

        if (!expectedSourceService.equalsIgnoreCase(sourceService)) {
            return false;
        }

        String expectedToken = computeSignature(sourceService, withoutToken(event));
        return MessageDigestHelper.constantTimeEquals(token, expectedToken);
    }

    public static void requireTrustedEvent(Map<String, Object> event, String expectedSourceService) {
        if (!isTrusted(event, expectedSourceService)) {
            throw new SecurityException("invalid internal service token");
        }
    }

    public static boolean isReplaySafe(Map<String, Object> event) {
        if (event == null) {
            return false;
        }

        Instant now = Instant.now();
        Instant occurredAt = parseInstant(stringValue(event.get("occurredAt")));
        Instant expiresAt = parseInstant(stringValue(event.get(EXPIRES_AT_FIELD)));
        String messageId = stringValue(event.get(MESSAGE_ID_FIELD));
        String nonce = stringValue(event.get(NONCE_FIELD));

        if (occurredAt == null || expiresAt == null || messageId == null || nonce == null) {
            return false;
        }

        if (occurredAt.isAfter(now.plus(MAX_FUTURE_DRIFT))) {
            return false;
        }
        if (Duration.between(occurredAt, now).compareTo(MAX_ACCEPTED_EVENT_AGE) > 0) {
            return false;
        }
        if (expiresAt.isBefore(now)) {
            return false;
        }

        cleanupExpired(now);
        Instant existingMessage = SEEN_MESSAGE_IDS.putIfAbsent(messageId, expiresAt);
        if (existingMessage != null && existingMessage.isAfter(now)) {
            return false;
        }

        Instant existingNonce = SEEN_NONCES.putIfAbsent(nonce, expiresAt);
        if (existingNonce != null && existingNonce.isAfter(now)) {
            SEEN_MESSAGE_IDS.remove(messageId);
            return false;
        }

        return true;
    }

    public static void requireReplaySafe(Map<String, Object> event) {
        if (!isReplaySafe(event)) {
            throw new SecurityException("replay protection validation failed");
        }
    }

    static void resetReplayCachesForTests() {
        SEEN_MESSAGE_IDS.clear();
        SEEN_NONCES.clear();
    }

    private static Map<String, Object> withoutToken(Map<String, Object> event) {
        Map<String, Object> copy = new LinkedHashMap<>(event);
        copy.remove(SERVICE_TOKEN_FIELD);
        return copy;
    }

    private static String computeSignature(String sourceService, Map<String, Object> event) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String canonicalPayload = canonicalize(sourceService, event);
            byte[] signatureBytes = mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("unable to sign internal service message", ex);
        }
    }

    private static String canonicalize(String sourceService, Map<String, Object> event) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(nullSafe(sourceService));
        joiner.add(nullSafe(stringValue(event.get("eventType"))));
        joiner.add(nullSafe(resolveVersion(event)));
        joiner.add(nullSafe(stringValue(event.get("requestId"))));
        joiner.add(nullSafe(stringValue(event.get("correlationId"))));
        joiner.add(nullSafe(stringValue(event.get(MESSAGE_ID_FIELD))));
        joiner.add(nullSafe(stringValue(event.get(NONCE_FIELD))));
        joiner.add(nullSafe(stringValue(event.get(EXPIRES_AT_FIELD))));
        joiner.add(canonicalValue(event.get("payload")));
        return joiner.toString();
    }

    private static void cleanupExpired(Instant now) {
        SEEN_MESSAGE_IDS.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(now));
        SEEN_NONCES.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(now));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String resolveVersion(Map<String, Object> event) {
        String version = stringValue(event.get("version"));
        if (version != null) {
            return version;
        }
        return stringValue(event.get("eventVersion"));
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                joiner.add(entry.getKey() + '=' + canonicalValue(entry.getValue()));
            }
            return joiner.toString();
        }
        if (value instanceof List<?> list) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (Object item : list) {
                joiner.add(canonicalValue(item));
            }
            return joiner.toString();
        }
        if (value.getClass().isArray()) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                joiner.add(canonicalValue(Array.get(value, index)));
            }
            return joiner.toString();
        }
        return value.toString();
    }

    private static String resolveSecret() {
        String secret = System.getenv(SECRET_ENV);
        if (secret == null || secret.isBlank()) {
            return TEST_FALLBACK_SECRET;
        }
        if (secret.startsWith(SECRET_MANAGER_PREFIX)) {
            String secretName = secret.substring(SECRET_MANAGER_PREFIX.length()).trim();
            if (secretName.isEmpty()) {
                return TEST_FALLBACK_SECRET;
            }
            String resolvedSecret = System.getProperty(SECRET_MANAGER_PROPERTY_PREFIX + secretName);
            return resolvedSecret == null || resolvedSecret.isBlank() ? TEST_FALLBACK_SECRET : resolvedSecret.trim();
        }
        return secret;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static final class MessageDigestHelper {
        private static boolean constantTimeEquals(String left, String right) {
            if (left == null || right == null) {
                return false;
            }
            byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
            byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
            if (leftBytes.length != rightBytes.length) {
                return false;
            }
            int result = 0;
            for (int index = 0; index < leftBytes.length; index++) {
                result |= leftBytes[index] ^ rightBytes[index];
            }
            return result == 0;
        }
    }
}
