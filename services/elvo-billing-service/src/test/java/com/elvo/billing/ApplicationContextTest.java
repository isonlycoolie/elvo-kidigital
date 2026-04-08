package com.elvo.billing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    void contextLoads() {
    }

    @Test
    void transportDefaultsShouldRequireTls() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(yaml.contains("sslmode: ${ELVO_DB_SSL_MODE:require}"));
        assertTrue(yaml.contains("enabled: ${ELVO_REDIS_SSL_ENABLED:true}"));
        assertTrue(yaml.contains("enabled: ${ELVO_RABBITMQ_SSL_ENABLED:true}"));
        assertTrue(yaml.contains("internal-service-secret: ${ELVO_INTERNAL_SERVICE_SECRET:sm://elvo-internal-service-secret}"));
    }
}
