package com.elvo.billing.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BillingFieldEncryptionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillingFieldEncryptionService.class);
    private static final String PREFIX = "enc:v1:";
    private static final String DEFAULT_ENV = "ELVO_BILLING_FIELD_ENCRYPTION_KEY";
    private static final String DEFAULT_PROPERTY = "elvo.security.field-encryption.key";
    private static final String TEST_FALLBACK_SECRET = "elvo-test-billing-field-encryption-secret";
    private static final String HMAC_ALGORITHM = "AES";
    private static final SecretKeySpec KEY_SPEC = new SecretKeySpec(sha256(resolveSecret()), HMAC_ALGORITHM);

    private BillingFieldEncryptionService() {
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        if (plainText.startsWith(PREFIX)) {
            return plainText;
        }

        try {
            byte[] iv = deriveIv(plainText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, KEY_SPEC, new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            LOGGER.error("billing_field_encrypt_failed reason={}", ex.getMessage());
            throw new IllegalStateException("Failed to encrypt billing field", ex);
        }
    }

    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank() || !encryptedValue.startsWith(PREFIX)) {
            return encryptedValue;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedValue.substring(PREFIX.length()));
            if (payload.length < 13) {
                return encryptedValue;
            }

            byte[] iv = new byte[12];
            byte[] cipherText = new byte[payload.length - 12];
            System.arraycopy(payload, 0, iv, 0, 12);
            System.arraycopy(payload, 12, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, KEY_SPEC, new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            LOGGER.warn("billing_field_decrypt_failed reason={}", ex.getMessage());
            return encryptedValue;
        }
    }

    private static String resolveSecret() {
        String configured = System.getProperty(DEFAULT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(DEFAULT_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return TEST_FALLBACK_SECRET;
        }
        return configured;
    }

    private static byte[] deriveIv(String value) {
        byte[] digest = sha256("iv:" + value);
        byte[] iv = new byte[12];
        System.arraycopy(digest, 0, iv, 0, iv.length);
        return iv;
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to initialize digest", ex);
        }
    }
}