package com.elvo.wallet.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChallengeCodeSecurityService {

    private static final int CHALLENGE_CODE_LENGTH = 4;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String hashPepper;

    public ChallengeCodeSecurityService(@Value("${elvo.security.challenge.hash-pepper:}") String hashPepper) {
        if (hashPepper == null || hashPepper.trim().isEmpty() || "elvo-wallet-challenge-pepper".equalsIgnoreCase(hashPepper.trim())) {
            throw new IllegalStateException(
                    "Challenge code hash pepper must be securely configured via environment variable ELVO_CHALLENGE_HASH_PEPPER_REF " +
                            "pointing to secret manager (e.g., sm://wallet-challenge-hash-pepper). " +
                            "Hardcoded or default pepper values are not permitted for security compliance."
            );
        }
        this.hashPepper = hashPepper;
    }

    public String generateCode() {
        int code = secureRandom.nextInt(10_000);
        return String.format("%04d", code);
    }

    public boolean isValidFormat(String rawCode) {
        if (rawCode == null) {
            return false;
        }
        String trimmed = rawCode.trim();
        return trimmed.length() == CHALLENGE_CODE_LENGTH && trimmed.chars().allMatch(Character::isDigit);
    }

    public String hashCode(String rawCode) {
        if (!isValidFormat(rawCode)) {
            throw new IllegalArgumentException("Challenge code must be exactly 4 digits");
        }
        return digest(hashPepper + ":" + rawCode.trim());
    }

    public String redact(String rawCode) {
        if (!isValidFormat(rawCode)) {
            return "redacted";
        }
        return "***" + rawCode.trim();
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
