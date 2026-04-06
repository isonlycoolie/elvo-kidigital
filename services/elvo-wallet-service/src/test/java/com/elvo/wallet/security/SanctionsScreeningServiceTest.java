package com.elvo.wallet.security;

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

import com.elvo.wallet.client.IdentityClientProperties;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SanctionsScreeningServiceTest {

    private IdentityClientProperties identityClientProperties;
    private InternalServiceJwtProperties internalJwtProperties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        identityClientProperties = new IdentityClientProperties();
        identityClientProperties.setBaseUrl("https://identity-service/internal");
        identityClientProperties.setSourceServiceName("wallet-service");
        identityClientProperties.setTokenTtlSeconds(60);
        identityClientProperties.setConnectTimeoutSeconds(3);
        identityClientProperties.setReadTimeoutSeconds(5);

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
    }

    @Test
    void evaluateShouldBlockSanctionedUser() {
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("https://identity-service/internal/compliance/sanctions-blacklist"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Source-Service", "wallet-service"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("{" +
                        "\"success\":true," +
                        "\"code\":\"SUCCESS\"," +
                        "\"data\":{\"sanctionedUserIds\":[\"" + userId + "\"],\"blacklistedTargets\":[\"+12345\"]}" +
                        "}", MediaType.APPLICATION_JSON));

        SanctionsScreeningService service = new SanctionsScreeningService(
                restTemplate,
                identityClientProperties,
                internalJwtProperties,
                internalJwtProperties.getSecret(),
                true,
                true,
                60,
                600);

        SanctionsScreeningService.ScreeningDecision decision = service.evaluate(userId, "+98765");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("sanctioned");
        server.verify();
    }

    @Test
    void evaluateShouldFailClosedWhenRefreshUnavailable() {
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("https://identity-service/internal/compliance/sanctions-blacklist"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        SanctionsScreeningService service = new SanctionsScreeningService(
                restTemplate,
                identityClientProperties,
                internalJwtProperties,
                internalJwtProperties.getSecret(),
                true,
                true,
                60,
                600);

        SanctionsScreeningService.ScreeningDecision decision = service.evaluate(userId, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("unavailable");
        server.verify();
    }

    @Test
    void evaluateShouldAllowWhenChecksDisabled() {
        SanctionsScreeningService service = new SanctionsScreeningService(
                restTemplate,
                identityClientProperties,
                internalJwtProperties,
                internalJwtProperties.getSecret(),
                false,
                true,
                60,
                600);

        SanctionsScreeningService.ScreeningDecision decision = service.evaluate(UUID.randomUUID(), "+12345");

        assertThat(decision.allowed()).isTrue();
    }

    private ClientHttpRequestInterceptor addJsonAcceptHeader() {
        return (request, body, execution) -> {
            request.getHeaders().setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            return execution.execute(request, body);
        };
    }
}
