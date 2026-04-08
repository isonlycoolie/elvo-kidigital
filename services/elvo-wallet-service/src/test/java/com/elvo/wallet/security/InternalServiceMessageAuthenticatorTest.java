package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InternalServiceMessageAuthenticatorTest {

    @Test
    void shouldTrustSignedEvent() {
        Map<String, Object> signed = InternalServiceMessageAuthenticator.signEvent(
                "elvo-wallet-service",
                Map.of(
                        "eventType", "wallet.transaction.committed",
                        "version", "v1",
                        "requestId", "req-1",
                        "correlationId", "corr-1",
                        "payload", Map.of("reservationId", "res-1", "state", "COMMITTED")));

        assertThat(InternalServiceMessageAuthenticator.isTrusted(signed, "elvo-wallet-service")).isTrue();
    }

    @Test
    void shouldRejectTamperedSignedEvent() {
        Map<String, Object> signed = InternalServiceMessageAuthenticator.signEvent(
                "elvo-wallet-service",
                Map.of(
                        "eventType", "wallet.transaction.reversed",
                        "version", "v1",
                        "requestId", "req-2",
                        "correlationId", "corr-2",
                        "payload", Map.of("reservationId", "res-2", "state", "REVERSED")));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = new HashMap<>((Map<String, Object>) signed.get("payload"));
        payload.put("state", "FAILED");

        Map<String, Object> tampered = new HashMap<>(signed);
        tampered.put("payload", payload);

        assertThat(InternalServiceMessageAuthenticator.isTrusted(tampered, "elvo-wallet-service")).isFalse();
    }
}
