package com.elvo.wallet.security;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class TransactionSigningChallengeService {

    private final SecretKey secretKey;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;

    public TransactionSigningChallengeService(
            @Value("${elvo.security.transaction-challenge.secret:${elvo.security.jwt.secret:elvo-wallet-service-jwt-secret-must-be-at-least-32-bytes}}") String secret,
            @Value("${elvo.security.transaction-challenge.issuer:elvo-wallet-service}") String issuer,
            @Value("${elvo.security.transaction-challenge.audience:wallet-transaction-challenge}") String audience,
            @Value("${elvo.security.transaction-challenge.ttl-seconds:300}") long ttlSeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
    }

    public String generateChallenge(UUID userId, String transactionType, BigDecimal amount, String destination) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(ttlSeconds)))
                .subject(userId == null ? "" : userId.toString())
                .claim("transactionType", normalizeType(transactionType))
                .claim("amount", normalizeAmount(amount))
                .claim("destination", normalizeDestination(destination))
                .claim("challengeId", UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    public boolean isValidChallenge(String challengeToken,
                                    UUID userId,
                                    String transactionType,
                                    BigDecimal amount,
                                    String destination) {
        if (challengeToken == null || challengeToken.isBlank() || userId == null || transactionType == null || amount == null) {
            return false;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(challengeToken)
                    .getPayload();

            String claimUserId = claims.getSubject();
            String claimType = claims.get("transactionType", String.class);
            String claimAmount = claims.get("amount", String.class);
            String claimDestination = claims.get("destination", String.class);

            return userId.toString().equals(claimUserId)
                    && normalizeType(transactionType).equals(normalizeType(claimType))
                    && normalizeAmount(amount).equals(claimAmount)
                    && normalizeDestination(destination).equals(normalizeDestination(claimDestination));
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeType(String transactionType) {
        if (transactionType == null) {
            return "";
        }
        return transactionType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private String normalizeDestination(String destination) {
        if (destination == null) {
            return "";
        }
        return destination.trim();
    }
}