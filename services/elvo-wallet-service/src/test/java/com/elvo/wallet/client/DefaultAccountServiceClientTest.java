package com.elvo.wallet.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.elvo.wallet.security.InternalServiceJwtProperties;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultAccountServiceClientTest {

    private AccountClientProperties properties;
    private InternalServiceJwtProperties internalJwtProperties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private DefaultAccountServiceClient client;

    @BeforeEach
    void setUp() {
        properties = new AccountClientProperties();
        properties.setBaseUrl("https://account-service/api/v1/internal/accounts");
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
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();

        client = new DefaultAccountServiceClient(restTemplate, properties, internalJwtProperties);
    }

    @Test
    void findAccountByUserIdShouldCallAccountServiceLookup() {
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("https://account-service/api/v1/internal/accounts/user/" + userId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Source-Service", "wallet-service"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("{" +
                        "\"success\":true," +
                        "\"message\":\"Account loaded\"," +
                        "\"data\":{" +
                        "\"accountId\":\"b0d7a61b-82db-4d33-a6b1-4a2b04f13d81\"," +
                        "\"userId\":\"" + userId + "\"," +
                        "\"ean\":\"EAN-123456789012\"," +
                        "\"accountType\":\"WALLET\"," +
                        "\"accountStatus\":\"ACTIVE\"," +
                        "\"kycStatus\":\"VERIFIED\"," +
                        "\"parentAccountId\":null," +
                        "\"createdAt\":\"2026-04-09T00:00:00Z\"," +
                        "\"updatedAt\":\"2026-04-09T00:00:00Z\"," +
                        "\"version\":3" +
                        "}" +
                        "}", MediaType.APPLICATION_JSON));

        AccountServiceClient.AccountSummary summary = client.findAccountByUserId(userId).orElseThrow();

        assertThat(summary.ean()).isEqualTo("EAN-123456789012");
        assertThat(summary.accountStatus()).isEqualTo("ACTIVE");
        server.verify();
    }

    @Test
    void validateWithdrawalShouldPostValidationRequest() {
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        server.expect(requestTo("https://account-service/api/v1/internal/accounts/validate-withdrawal"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Source-Service", "wallet-service"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{" +
                        "\"sourceAccountId\":\"" + sourceAccountId + "\"," +
                        "\"destinationAccountId\":\"" + destinationAccountId + "\"," +
                        "\"amount\":25.50," +
                        "\"requestId\":\"req-1\"," +
                        "\"correlationId\":\"corr-1\"," +
                        "\"sourceService\":\"wallet-service\"," +
                        "\"sourceIp\":\"10.0.0.10\"," +
                        "\"sourceUserAgent\":\"wallet-service-client\"" +
                        "}"))
                .andRespond(withSuccess("{" +
                        "\"success\":true," +
                        "\"message\":\"Withdrawal validation completed\"," +
                        "\"data\":{" +
                        "\"allowed\":true," +
                        "\"reason\":null," +
                        "\"accountId\":\"" + sourceAccountId + "\"," +
                        "\"ean\":\"EAN-123456789012\"," +
                        "\"accountStatus\":\"ACTIVE\"," +
                        "\"kycStatus\":\"VERIFIED\"" +
                        "}" +
                        "}", MediaType.APPLICATION_JSON));

        AccountServiceClient.AccountValidationResult result = client.validateWithdrawal(
                new AccountServiceClient.AccountValidationRequest(
                        sourceAccountId,
                        destinationAccountId,
                        new BigDecimal("25.50"),
                        "req-1",
                        "corr-1",
                        "wallet-service",
                        "10.0.0.10",
                        "wallet-service-client"));

        assertThat(result.allowed()).isTrue();
        assertThat(result.accountId()).isEqualTo(sourceAccountId);
        server.verify();
    }
}