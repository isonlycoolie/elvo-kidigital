package com.elvo.wallet;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ApplicationContextTest {

    @Test
    void contextLoads() {
    }

    @Test
    void jwtDefaultsShouldBeServiceAndEnvironmentScoped() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(yaml.contains("issuer: ${ELVO_JWT_ISSUER:elvo-identity-service-${spring.profiles.active:dev}}"));
        assertTrue(yaml.contains("audience: ${ELVO_JWT_AUDIENCE:elvo-wallet-service-${spring.profiles.active:dev}}"));
        assertTrue(yaml.contains("issuer: ${ELVO_INTERNAL_JWT_ISSUER:elvo-wallet-service-internal-${spring.profiles.active:dev}}"));
        assertTrue(yaml.contains("audience: ${ELVO_INTERNAL_JWT_AUDIENCE:elvo-wallet-service-internal-${spring.profiles.active:dev}}"));
    }
}
