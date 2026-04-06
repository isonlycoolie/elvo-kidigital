package com.elvo.wallet.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;

class InternalServiceJwtAuthenticationFilterTest {

    private InternalServiceJwtProperties properties;
    private InternalServiceAuthorizationMatrix authorizationMatrix;
    private InternalServiceJwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        properties = new InternalServiceJwtProperties();
        properties.setSecret("elvo-wallet-internal-jwt-secret-must-have-at-least-32-chars");
        properties.setIssuer("elvo-internal-auth");
        properties.setAudience("elvo-wallet-service");
        properties.setRequiredRole("INTERNAL_SERVICE");
        properties.setSourceServiceClaim("sourceService");
        properties.setServiceIdentityClaim("serviceIdentity");

        authorizationMatrix = mock(InternalServiceAuthorizationMatrix.class);
        when(authorizationMatrix.isAllowed(anyString(), eq("GET"), anyString())).thenReturn(true);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new InternalServiceJwtAuthenticationFilter(properties, authorizationMatrix, objectMapper);
    }

    @Test
    void shouldAllowValidSourceServiceIdentityAndHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/wallets/user-1/balance");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token("billing-service", "billing-service"));
        request.addHeader("X-Source-Service", "billing-service");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    @Test
    void shouldRejectSpoofedSourceServiceHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/wallets/user-1/balance");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token("billing-service", "billing-service"));
        request.addHeader("X-Source-Service", "identity-service");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Source service header does not match signed token claim");
    }

    private String token(String sourceService, String serviceIdentity) {
        SecretKey key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .claim(properties.getSourceServiceClaim(), sourceService)
                .claim(properties.getServiceIdentityClaim(), serviceIdentity)
                .claim("roles", List.of(properties.getRequiredRole()))
                .signWith(key)
                .compact();
    }
}
