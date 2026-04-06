package com.elvo.wallet.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EtcCodeSecurityService {

    private final String hashPepper;

    public EtcCodeSecurityService(@Value("${elvo.security.etc.hash-pepper:}") String hashPepper) {
        // SECURITY: Reject hardcoded default pepper. Must use secret manager.
        if (hashPepper == null || hashPepper.trim().isEmpty() || "elvo-wallet-etc-pepper".equalsIgnoreCase(hashPepper.trim())) {
            throw new IllegalStateException(
                "ETC code hash pepper must be securely configured via environment variable ELVO_ETC_HASH_PEPPER_REF " +
                "pointing to secret manager (e.g., sm://wallet-etc-hash-pepper). " +
                "Hardcoded or default pepper values are not permitted for security compliance."
            );
        }
        this.hashPepper = hashPepper;
    }

    public String hashCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("ETC code must not be blank");
        }
        return digest(hashPepper + ":" + rawCode.trim());
    }

    public String redact(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return "redacted";
        }
        String trimmed = rawCode.trim();
        int keep = Math.min(4, trimmed.length());
        return "***" + trimmed.substring(trimmed.length() - keep);
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