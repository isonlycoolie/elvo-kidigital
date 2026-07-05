package com.elvo.billing.service.settlement;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.elvo.billing.security.InternalServiceJwtProperties;
import com.elvo.billing.security.InternalServiceJwtTokenGenerator;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

@Tag("cross-service")
class BillingWalletSettlementWireMockTest {

    private WireMockServer walletServer;
    private BillingWalletSettlementService settlementService;

    @BeforeEach
    void setUp() {
        walletServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        walletServer.start();

        InternalServiceJwtProperties properties = new InternalServiceJwtProperties();
        properties.setSecret("billing-wallet-settlement-test-secret-at-least-32-chars");
        properties.setIssuer("elvo-wallet-service-internal-test");
        properties.setAudience("elvo-wallet-service-internal-test");

        settlementService = new BillingWalletSettlementService(
                "http://localhost:" + walletServer.port(),
                new InternalServiceJwtTokenGenerator(properties),
                "billing-service");
    }

    @AfterEach
    void tearDown() {
        if (walletServer != null) {
            walletServer.stop();
        }
    }

    @Test
    void reserveShouldCallWalletWithInternalJwt() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        walletServer.stubFor(post(urlEqualTo("/api/v1/internal/wallets/" + userId + "/reserve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success":true,"message":"reserved","walletId":"%s","transactionId":"%s"}
                                """.formatted(walletId, reservationId))));

        BillingWalletSettlementService.WalletReservation reservation = settlementService.reserve(
                userId,
                new BigDecimal("1500.00"),
                "settlement-test-key");

        assertThat(reservation.walletId()).isEqualTo(walletId);
        assertThat(reservation.reservationId()).isEqualTo(reservationId);
        walletServer.verify(postRequestedFor(urlEqualTo("/api/v1/internal/wallets/" + userId + "/reserve"))
                .withHeader("X-Source-Service", equalTo("billing-service")));
    }
}
