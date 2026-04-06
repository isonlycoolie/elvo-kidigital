package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class TransactionSigningChallengeServiceTest {

    private TransactionSigningChallengeService service;

    @BeforeEach
    void setUp() {
        SecretManagerService secretManagerService = new SecretManagerService(new org.springframework.mock.env.MockEnvironment()
                .withProperty("elvo.secret-manager.secrets.wallet-transaction-challenge-secret", "wallet-transaction-challenge-secret-must-be-at-least-32-bytes"));
        service = new TransactionSigningChallengeService(
                secretManagerService,
                new InMemoryTransactionChallengeReplayProtectionService(300),
                "wallet-transaction-challenge-secret-must-be-at-least-32-bytes",
                "elvo-wallet-service",
                "wallet-transaction-challenge",
                5);
    }

    @Test
    void shouldRejectChallengeReplay() {
        UUID userId = UUID.randomUUID();
        String token = service.generateChallenge(userId, "withdrawal", BigDecimal.valueOf(25), "0700000000");

        assertThat(service.isValidChallenge(token, userId, "withdrawal", BigDecimal.valueOf(25), "0700000000")).isTrue();
        assertThat(service.isValidChallenge(token, userId, "withdrawal", BigDecimal.valueOf(25), "0700000000")).isFalse();
    }

    @Test
    void shouldBindNonceToChallengeId() {
        UUID userId = UUID.randomUUID();
        String token = service.generateChallenge(userId, "transfer", BigDecimal.valueOf(10), "destination");

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor("wallet-transaction-challenge-secret-must-be-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .requireIssuer("elvo-wallet-service")
                .requireAudience("wallet-transaction-challenge")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals(claims.getId(), claims.get("nonce", String.class));
    }
}