package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InternalServiceAuthorizationMatrixTest {

    @Test
    void shouldAllowConfiguredServiceAndEndpoint() {
        InternalServiceAuthorizationProperties properties = new InternalServiceAuthorizationProperties();
        properties.setServiceRules(Map.of(
                "billing-service", List.of("POST:/api/v1/internal/wallets/*/reserve")
        ));

        InternalServiceAuthorizationMatrix matrix = new InternalServiceAuthorizationMatrix(properties);

        boolean allowed = matrix.isAllowed(
                "billing-service",
                "POST",
                "/api/v1/internal/wallets/11111111-1111-1111-1111-111111111111/reserve"
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectServiceNotConfiguredForEndpoint() {
        InternalServiceAuthorizationProperties properties = new InternalServiceAuthorizationProperties();
        properties.setServiceRules(Map.of(
                "billing-service", List.of("POST:/api/v1/internal/wallets/*/reserve"),
                "saga-service", List.of("POST:/api/v1/internal/wallets/*/reverse")
        ));

        InternalServiceAuthorizationMatrix matrix = new InternalServiceAuthorizationMatrix(properties);

        boolean allowed = matrix.isAllowed(
                "billing-service",
                "POST",
                "/api/v1/internal/wallets/11111111-1111-1111-1111-111111111111/reverse"
        );

        assertThat(allowed).isFalse();
    }

    @Test
    void shouldRejectUnknownService() {
        InternalServiceAuthorizationProperties properties = new InternalServiceAuthorizationProperties();
        properties.setServiceRules(Map.of(
                "identity-service", List.of("GET:/api/v1/internal/wallets/*/balance")
        ));

        InternalServiceAuthorizationMatrix matrix = new InternalServiceAuthorizationMatrix(properties);

        boolean allowed = matrix.isAllowed(
                "unknown-service",
                "GET",
                "/api/v1/internal/wallets/11111111-1111-1111-1111-111111111111/balance"
        );

        assertThat(allowed).isFalse();
    }
}
