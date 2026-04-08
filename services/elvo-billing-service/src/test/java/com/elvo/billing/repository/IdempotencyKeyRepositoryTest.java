package com.elvo.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.elvo.billing.entity.IdempotencyKey;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyKeyRepositoryTest {

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void saveShouldEncryptStoredResponsePayload() {
        IdempotencyKey key = new IdempotencyKey();
        key.setIdempotencyKey("idem-encrypt-" + UUID.randomUUID());
        key.setOperation("PAYMENT_CREATE");
        key.setRequestHash("hash-123");
        key.setResponsePayload("{\"status\":\"SUCCESS\"}");

        IdempotencyKey saved = idempotencyKeyRepository.save(key);

        assertThat(saved.getResponsePayload()).isEqualTo("{\"status\":\"SUCCESS\"}");
        assertThat(jdbcTemplate.queryForObject(
                "select response_payload from idempotency_keys where idempotency_key = ?",
                String.class,
                saved.getIdempotencyKey()))
                .startsWith("enc:v1:");
    }
}