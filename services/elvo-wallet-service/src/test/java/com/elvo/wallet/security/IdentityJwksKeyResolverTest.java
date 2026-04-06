package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.elvo.wallet.client.IdentityClientProperties;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class IdentityJwksKeyResolverTest {

    private static final String BASE_URL = "https://identity.local/internal";

    @Test
    void shouldResolveKeyAndRefreshOnRollover() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        IdentityClientProperties properties = new IdentityClientProperties();
        properties.setBaseUrl(BASE_URL);

        KeyPair activeKeyPair = generateRsaKeyPair();
        KeyPair rotatedKeyPair = generateRsaKeyPair();
        String activeKid = "kid-1";
        String rotatedKid = "kid-2";

        server.expect(requestTo("https://identity.local/.well-known/jwks.json"))
            .andExpect(method(GET))
            .andRespond(withSuccess(jwksJson(activeKid, activeKeyPair), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://identity.local/.well-known/jwks.json"))
            .andExpect(method(GET))
            .andRespond(withSuccess(jwksJson(rotatedKid, rotatedKeyPair), MediaType.APPLICATION_JSON));

        IdentityJwksKeyResolver resolver = new IdentityJwksKeyResolver(restTemplate, properties);
        PublicKey activeKey = resolver.resolve(activeKid);
        assertThat(activeKey).isNotNull();

        PublicKey rotatedKey = resolver.resolve(rotatedKid);
        assertThat(rotatedKey).isNotNull();

        server.verify();
    }

    @Test
    void shouldRejectUnknownKidWhenJWKSDoesNotContainIt() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        IdentityClientProperties properties = new IdentityClientProperties();
        properties.setBaseUrl(BASE_URL);

        KeyPair keyPair = generateRsaKeyPair();
        server.expect(requestTo("https://identity.local/.well-known/jwks.json"))
                .andExpect(method(GET))
                .andRespond(withSuccess(jwksJson("kid-1", keyPair), MediaType.APPLICATION_JSON));

        IdentityJwksKeyResolver resolver = new IdentityJwksKeyResolver(restTemplate, properties);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> resolver.resolve("kid-unknown"));
        assertThat(ex.getMessage()).isEqualTo("Token key id is invalid");
        server.verify();
    }

    @Test
    void shouldRejectNonHttpsJwksBaseUrl() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        IdentityClientProperties properties = new IdentityClientProperties();

        IllegalArgumentException propertyException = assertThrows(
            IllegalArgumentException.class,
            () -> properties.setBaseUrl("http://identity.local/internal")
        );
        assertThat(propertyException.getMessage()).contains("must use HTTPS protocol");

        properties.setBaseUrl("https://identity.local/internal");
        IdentityJwksKeyResolver resolver = new IdentityJwksKeyResolver(restTemplate, properties);
        assertThat(resolver).isNotNull();
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

    private String jwksJson(String kid, KeyPair keyPair) {
        java.security.interfaces.RSAPublicKey publicKey = (java.security.interfaces.RSAPublicKey) keyPair.getPublic();
        return "{\"keys\":[{" 
                + "\"kid\":\"" + kid + "\"," 
                + "\"kty\":\"RSA\"," 
                + "\"alg\":\"RS256\"," 
                + "\"use\":\"sig\"," 
                + "\"n\":\"" + encodeUnsigned(publicKey.getModulus().toByteArray()) + "\"," 
                + "\"e\":\"" + encodeUnsigned(publicKey.getPublicExponent().toByteArray()) + "\""
                + "}]}";
    }

    private String encodeUnsigned(byte[] value) {
        byte[] normalized = value;
        if (value.length > 1 && value[0] == 0) {
            normalized = new byte[value.length - 1];
            System.arraycopy(value, 1, normalized, 0, normalized.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(normalized);
    }
}