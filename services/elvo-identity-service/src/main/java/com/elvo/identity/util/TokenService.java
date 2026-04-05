package com.elvo.identity.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class TokenService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final SecureRandom secureRandom = new SecureRandom();

    public TokenPayload generateAccessToken(UUID userId, String ean) {
        Instant expiresAt = Instant.now().plus(ACCESS_TOKEN_TTL);
        String token = encode(userId + ":" + ean + ":" + expiresAt.toEpochMilli() + ":" + randomTokenPart());
        return new TokenPayload(token, expiresAt);
    }

    public TokenPayload generateRefreshToken(UUID userId) {
        Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL);
        String token = encode("REFRESH:" + userId + ":" + expiresAt.toEpochMilli() + ":" + randomTokenPart());
        return new TokenPayload(token, expiresAt);
    }

    private String randomTokenPart() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public record TokenPayload(String token, Instant expiresAt) {
    }
}
