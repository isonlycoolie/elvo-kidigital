package com.elvo.identity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class DefaultWalletProvisioningClientUnitTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void createWalletShouldCallInternalWalletEndpointWithBearerAuthorization() throws IOException {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> sourceHeader = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>("");

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/internal/wallets/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sourceHeader.set(exchange.getRequestHeaders().getFirst("X-Source-Service"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();

        ProvisioningClientProperties properties = new ProvisioningClientProperties();
        properties.setWalletBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setSourceServiceName("identity-service");
        properties.setInternalAuthToken("internal-token-value");
        properties.setMaxAttempts(1);
        properties.setRetryBackoffMs(1);
        properties.setRetryBackoffMultiplier(1.0);
        properties.setRetryMaxBackoffMs(1);

        DefaultWalletProvisioningClient client = new DefaultWalletProvisioningClient(
                properties,
                new ProvisioningRetryExecutor(properties));

        UUID userId = UUID.randomUUID();
        client.createWallet(userId, "idem-wallet-create-001");

        assertEquals("POST", method.get());
        assertEquals("/api/v1/internal/wallets/" + userId, path.get());
        assertEquals("Bearer internal-token-value", authHeader.get());
        assertEquals("identity-service", sourceHeader.get());
        assertTrue(body.get().contains("{}") || body.get().isBlank());
    }
}
