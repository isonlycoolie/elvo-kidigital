package com.elvo.billing.adapter;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingAdapterFrameworkTest {

    @Test
    void wireMockServerShouldStubAdapterEndpoint() {
        WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        try {
            configureFor("localhost", wireMockServer.port());

            stubFor(get(urlEqualTo("/adapter/lookup"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"lookupStatus\":\"SUCCESS\",\"referenceNumber\":\"REF-001\"}")));

            String responseBody = java.net.http.HttpClient.newHttpClient()
                .send(
                    java.net.http.HttpRequest.newBuilder()
                        .GET()
                        .uri(java.net.URI.create("http://localhost:" + wireMockServer.port() + "/adapter/lookup"))
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()
                )
                .body();

            assertEquals("{\"lookupStatus\":\"SUCCESS\",\"referenceNumber\":\"REF-001\"}", responseBody);
            wireMockServer.verify(getRequestedFor(urlEqualTo("/adapter/lookup")));
        } catch (Exception ex) {
            throw new RuntimeException("Adapter framework smoke test failed", ex);
        } finally {
            wireMockServer.stop();
        }
    }
}