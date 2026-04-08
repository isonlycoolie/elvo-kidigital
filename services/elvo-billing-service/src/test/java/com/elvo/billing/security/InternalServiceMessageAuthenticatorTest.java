package com.elvo.billing.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class InternalServiceMessageAuthenticatorTest {

    @org.junit.jupiter.api.BeforeEach
    void resetReplayState() {
        InternalServiceMessageAuthenticator.resetReplayCachesForTests();
    }

    @Test
    void shouldTrustSignedEvent() {
        Map<String, Object> signed = InternalServiceMessageAuthenticator.signEvent(
                "elvo-billing-service",
                Map.of(
                        "eventType", "billing.transaction.completed",
                        "eventVersion", "v1",
                        "requestId", "req-1",
                        "correlationId", "corr-1",
                        "payload", Map.of("transactionId", "tx-1", "status", "SUCCESS")));

        assertThat(InternalServiceMessageAuthenticator.isTrusted(signed, "elvo-billing-service")).isTrue();
    }

    @Test
    void shouldRejectTamperedSignedEvent() {
        Map<String, Object> signed = InternalServiceMessageAuthenticator.signEvent(
                "elvo-billing-service",
                Map.of(
                        "eventType", "billing.transaction.completed",
                        "eventVersion", "v1",
                        "requestId", "req-2",
                        "correlationId", "corr-2",
                        "payload", Map.of("transactionId", "tx-2", "status", "SUCCESS")));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = new HashMap<>((Map<String, Object>) signed.get("payload"));
        payload.put("status", "FAILED");

        Map<String, Object> tampered = new HashMap<>(signed);
        tampered.put("payload", payload);

        assertThat(InternalServiceMessageAuthenticator.isTrusted(tampered, "elvo-billing-service")).isFalse();
    }

    @Test
    void shouldRejectReplayOfSameMessageAndNonce() {
        Map<String, Object> unsigned = new HashMap<>();
        unsigned.put("eventType", "billing.transaction.completed");
        unsigned.put("eventVersion", "v1");
        unsigned.put("requestId", "req-3");
        unsigned.put("correlationId", "corr-3");
        Instant occurredAt = Instant.now();
        unsigned.put("occurredAt", occurredAt.toString());
        unsigned.put("messageId", "msg-3");
        unsigned.put("nonce", "nonce-3");
        unsigned.put("expiresAt", occurredAt.plus(5, ChronoUnit.MINUTES).toString());
        unsigned.put("payload", Map.of("transactionId", "tx-3"));

        Map<String, Object> signed = InternalServiceMessageAuthenticator.signEvent("elvo-billing-service", unsigned);

        assertThat(InternalServiceMessageAuthenticator.isReplaySafe(signed)).isTrue();
        assertThat(InternalServiceMessageAuthenticator.isReplaySafe(signed)).isFalse();
    }
}
