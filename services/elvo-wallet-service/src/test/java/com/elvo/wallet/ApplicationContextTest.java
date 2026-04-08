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

    @Test
    void transportDefaultsShouldRequireTls() throws Exception {
        String mainYaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String databaseYaml = Files.readString(Path.of("config/application-database.yml"));
        String messagingYaml = Files.readString(Path.of("config/application-messaging.yml"));
        assertTrue(mainYaml.contains("base-url: ${ELVO_IDENTITY_INTERNAL_BASE_URL:https://localhost:8381/internal}"));
        assertTrue(mainYaml.contains("enforce-https: ${ELVO_IDENTITY_TLS_ENFORCE_HTTPS:true}"));
        assertTrue(databaseYaml.contains("sslmode: ${ELVO_DB_SSL_MODE:require}"));
        assertTrue(databaseYaml.contains("ssl: ${ELVO_DB_SSL_ENABLED:true}"));
        assertTrue(messagingYaml.contains("enabled: ${RABBITMQ_SSL_ENABLED:true}"));
        assertTrue(messagingYaml.contains("enabled: ${ELVO_REDIS_SSL_ENABLED:true}"));
    }
}
