package com.elvo.wallet.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.elvo.wallet.security.InternalServiceJwtProperties;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultIdentityServiceClientTest {

    private IdentityClientProperties properties;
    private InternalServiceJwtProperties internalJwtProperties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private DefaultIdentityServiceClient client;

    @BeforeEach
    void setUp() {
        properties = new IdentityClientProperties();
        properties.setBaseUrl("https://identity-service/internal");
        properties.setSourceServiceName("wallet-service");
        properties.setClientSourceIp("wallet-service");
        properties.setClientSourceUserAgent("wallet-service-client");
        properties.setTokenTtlSeconds(60);

        internalJwtProperties = new InternalServiceJwtProperties();
        internalJwtProperties.setSecret("wallet-internal-jwt-secret-that-is-at-least-32-bytes");
        internalJwtProperties.setIssuer("elvo-internal-auth");
        internalJwtProperties.setAudience("elvo-wallet-service");
        internalJwtProperties.setRequiredRole("INTERNAL_SERVICE");
        internalJwtProperties.setSourceServiceClaim("sourceService");
        internalJwtProperties.setServiceIdentityClaim("serviceIdentity");

        restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(addJsonAcceptHeader());
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();

        client = new DefaultIdentityServiceClient(restTemplate, properties, internalJwtProperties);
    }

    @Test
    void isUserActiveShouldCallIdentityInternalEndpoint() {
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("https://identity-service/internal/users/" + userId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Source-Service", "wallet-service"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("{" +
                        "\"success\":true," +
                        "\"code\":\"SUCCESS\"," +
                        "\"message\":\"User loaded\"," +
                        "\"data\":{\"active\":true}" +
                        "}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://identity-service/internal/users/" + userId + "/kyc-status"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Source-Service", "wallet-service"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
            .andRespond(withSuccess("{" +
                "\"success\":true," +
                "\"code\":\"SUCCESS\"," +
                "\"message\":\"KYC status loaded\"," +
                "\"data\":{\"verified\":true,\"reverificationRequired\":false,\"documentExpired\":false}" +
                "}", MediaType.APPLICATION_JSON));

        boolean active = client.isUserActive(userId);

        assertThat(active).isTrue();
        server.verify();
    }

    @Test
    void verifyEspShouldUseSignedInternalRequest() {
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("https://identity-service/internal/verify-esp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Source-Service", "wallet-service"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{" +
                        "\"userId\":\"" + userId + "\"," +
                        "\"espCode\":\"ESP-1234\"," +
                        "\"sourceIp\":\"wallet-service\"," +
                        "\"sourceUserAgent\":\"wallet-service-client\"" +
                        "}"))
                .andRespond(withSuccess("{" +
                        "\"success\":true," +
                        "\"code\":\"SUCCESS\"," +
                        "\"message\":\"ESP verification completed\"," +
                        "\"data\":{\"verified\":true}" +
                        "}", MediaType.APPLICATION_JSON));

        boolean verified = client.verifyEsp(userId, "ESP-1234");

        assertThat(verified).isTrue();
        server.verify();
    }

    @Test
    void verifyEacShouldReturnFalseWhenIdentityDeclines() {
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("https://identity-service/internal/verify-eac"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Source-Service", "wallet-service"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("{" +
                        "\"success\":true," +
                        "\"code\":\"SUCCESS\"," +
                        "\"message\":\"EAC verification completed\"," +
                        "\"data\":{\"verified\":false}" +
                        "}", MediaType.APPLICATION_JSON));

        boolean verified = client.verifyEac(userId, "EAC-7777");

        assertThat(verified).isFalse();
        server.verify();
    }

    @Test
    void shouldRejectNonHttpsBaseUrlWhenTlsIsEnforced() {
        IdentityClientProperties insecureProperties = new IdentityClientProperties();
        insecureProperties.setBaseUrl("http://identity-service/internal");
        insecureProperties.setSourceServiceName("wallet-service");
        insecureProperties.setClientSourceIp("wallet-service");
        insecureProperties.setClientSourceUserAgent("wallet-service-client");
        insecureProperties.setTokenTtlSeconds(60);

        DefaultIdentityServiceClient insecureClient = new DefaultIdentityServiceClient(restTemplate, insecureProperties, internalJwtProperties);

        boolean active = insecureClient.isUserActive(UUID.randomUUID());
        assertThat(active).isFalse();
    }

        @Test
        void isUserActiveShouldFailWhenKycReverificationIsRequired() {
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("https://identity-service/internal/users/" + userId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{" +
                "\"success\":true," +
                "\"code\":\"SUCCESS\"," +
                "\"message\":\"User loaded\"," +
                "\"data\":{\"active\":true}" +
                "}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://identity-service/internal/users/" + userId + "/kyc-status"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{" +
                "\"success\":true," +
                "\"code\":\"SUCCESS\"," +
                "\"message\":\"KYC status loaded\"," +
                "\"data\":{\"verified\":true,\"reverificationRequired\":true,\"documentExpired\":false}" +
                "}", MediaType.APPLICATION_JSON));

        boolean active = client.isUserActive(userId);

        assertThat(active).isFalse();
        server.verify();
        }

        @Test
        void isUserActiveShouldFailWhenKycDocumentExpired() {
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("https://identity-service/internal/users/" + userId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{" +
                "\"success\":true," +
                "\"code\":\"SUCCESS\"," +
                "\"message\":\"User loaded\"," +
                "\"data\":{\"active\":true}" +
                "}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://identity-service/internal/users/" + userId + "/kyc-status"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{" +
                "\"success\":true," +
                "\"code\":\"SUCCESS\"," +
                "\"message\":\"KYC status loaded\"," +
                "\"data\":{\"verified\":true,\"reverificationRequired\":false,\"documentExpired\":true}" +
                "}", MediaType.APPLICATION_JSON));

        boolean active = client.isUserActive(userId);

        assertThat(active).isFalse();
        server.verify();
        }

    private ClientHttpRequestInterceptor addJsonAcceptHeader() {
        return (request, body, execution) -> {
            request.getHeaders().setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            return execution.execute(request, body);
        };
    }
}
