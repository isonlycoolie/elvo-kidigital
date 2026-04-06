package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserJwtAuthenticationFilterTest {

    private static final String ISSUER = "elvo-identity-service";
    private static final String AUDIENCE = "elvo-platform";
    private static final String KEY_ID = "identity-key-01";
    private static final String PREVIOUS_KEY_ID = "identity-key-00";

    private UserJwtProperties properties;
    private UserJwtAuthenticationFilter filter;
    private KeyPair keyPair;
    private KeyPair previousKeyPair;
    private UserTokenRevocationChecker tokenRevocationChecker;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        properties = new UserJwtProperties();
        properties.setIssuer(ISSUER);
        properties.setAudience(AUDIENCE);
        properties.setSigningKeyId(KEY_ID);
        properties.setPreviousSigningKeyId(PREVIOUS_KEY_ID);

        keyPair = generateRsaKeyPair();
        properties.setSigningPublicKeyPem(toPublicPem(keyPair));
        previousKeyPair = generateRsaKeyPair();
        properties.setPreviousSigningPublicKeyPem(toPublicPem(previousKeyPair));

        tokenRevocationChecker = mock(UserTokenRevocationChecker.class);
        when(tokenRevocationChecker.isRevoked(anyString())).thenReturn(false);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new UserJwtAuthenticationFilter(properties, objectMapper, tokenRevocationChecker);
    }

    @Test
    void shouldAuthenticateValidUserBearerToken() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wallets/me/balance");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId, "ACCESS"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(403);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(UserJwtPrincipal.class);
        UserJwtPrincipal principal = (UserJwtPrincipal) authentication.getPrincipal();
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.ean()).isEqualTo("ELVO-USER-0001");
        assertThat(authentication.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    void shouldRejectMissingBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wallets/me/balance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Missing or invalid user bearer token");
    }

    @Test
    void shouldRejectRefreshTokenForWalletEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wallets/me/balance");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token(UUID.randomUUID(), "REFRESH"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Token type is invalid");
    }

    @Test
    void shouldRejectBasicAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wallets/me/balance");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Missing or invalid user bearer token");
    }

    @Test
    void shouldRejectRevokedJti() throws Exception {
        UUID userId = UUID.randomUUID();
        when(tokenRevocationChecker.isRevoked(anyString())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wallets/me/balance");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId, "ACCESS"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Token is revoked");
    }

    @Test
    void shouldAuthenticateTokenSignedWithPreviousRolloverKey() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wallets/me/balance");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token(previousKeyPair, PREVIOUS_KEY_ID, userId, "ACCESS"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(403);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
    }

    @Test
    void shouldResolveTokenKeyFromJwksResolver() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wallets/me/balance");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token(keyPair, KEY_ID, userId, "ACCESS"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        IdentityJwksKeyResolver identityJwksKeyResolver = mock(IdentityJwksKeyResolver.class);
        when(identityJwksKeyResolver.resolve(KEY_ID)).thenReturn(keyPair.getPublic());
        UserJwtAuthenticationFilter jwksFilter = new UserJwtAuthenticationFilter(properties, new ObjectMapper().registerModule(new JavaTimeModule()), tokenRevocationChecker, null, identityJwksKeyResolver);

        jwksFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(403);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
    }

    private String token(UUID userId, String tokenType) {
        return token(keyPair, KEY_ID, userId, tokenType);
    }

    private String token(KeyPair signingKeyPair, String keyId, UUID userId, String tokenType) {
        return Jwts.builder()
                .issuer(ISSUER)
                .header().keyId(keyId).and()
                .audience().add(AUDIENCE).and()
                .subject(userId.toString())
            .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(120)))
                .claim("ean", "ELVO-USER-0001")
                .claim("roles", List.of("USER"))
                .claim("scopes", List.of("wallet:read"))
                .claim("tokenType", tokenType)
                .signWith(signingKeyPair.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate RSA key pair", ex);
        }
    }

    private String toPublicPem(KeyPair keyPair) {
        String encoded = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }
}
