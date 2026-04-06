package com.elvo.wallet.security;

import java.security.SecureRandom;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EtcCodePolicyService {

    private static final String ETC_PREFIX = "ETC-";
    private static final String ENTROPY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final SecureRandom secureRandom = new SecureRandom();
    private final int minSecureLength;
    private final long maxExpiryWindowSeconds;

    public EtcCodePolicyService(
            @Value("${elvo.security.etc.min-secure-length:12}") int minSecureLength,
            @Value("${elvo.security.etc.max-expiry-window-seconds:900}") long maxExpiryWindowSeconds
    ) {
        this.minSecureLength = minSecureLength;
        this.maxExpiryWindowSeconds = maxExpiryWindowSeconds;
    }

    public String generateSecureCode() {
        StringBuilder builder = new StringBuilder(ETC_PREFIX);
        for (int i = 0; i < minSecureLength; i++) {
            int idx = secureRandom.nextInt(ENTROPY_ALPHABET.length());
            builder.append(ENTROPY_ALPHABET.charAt(idx));
        }
        return builder.toString();
    }

    public boolean hasRequiredEntropy(String code) {
        if (code == null || !code.startsWith(ETC_PREFIX)) {
            return false;
        }

        String token = code.substring(ETC_PREFIX.length());
        if (token.length() < minSecureLength) {
            return false;
        }

        return token.chars().allMatch(c -> Character.isUpperCase(c) || Character.isDigit(c));
    }

    public boolean isExpiryWithinWindow(Instant expiresAt, Instant now) {
        if (expiresAt == null || now == null || expiresAt.isBefore(now)) {
            return false;
        }
        Instant latestAllowed = now.plusSeconds(maxExpiryWindowSeconds);
        return !expiresAt.isAfter(latestAllowed);
    }
}