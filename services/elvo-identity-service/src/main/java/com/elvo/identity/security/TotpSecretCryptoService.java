package com.elvo.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TotpSecretCryptoService {

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final String KEY_ALGO = "AES";

    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpSecretCryptoService(
            SecretManagerService secretManagerService,
            @Value("${elvo.security.totp.encryption-secret-ref:sm://identity-totp-encryption-secret}") String encryptionSecretRef) {
        String resolved = secretManagerService.resolve(
                "identity-totp-encryption-secret",
                encryptionSecretRef,
                "ELVO_TOTP_ENCRYPTION_SECRET",
                "change-this-totp-encryption-secret");
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException("TOTP encryption secret must be configured");
        }
        if (resolved.getBytes(StandardCharsets.UTF_8).length < 16) {
            throw new IllegalStateException("TOTP encryption secret must be at least 16 bytes");
        }
        this.key = sha256(resolved);
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value must not be blank");
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        byte[] cipherText;
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGO), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt TOTP secret", ex);
        }
        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException("Encrypted value must not be blank");
        }
        byte[] combined = Base64.getUrlDecoder().decode(encryptedValue);
        if (combined.length <= IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Encrypted value is invalid");
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] cipherText = new byte[combined.length - IV_LENGTH_BYTES];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, KEY_ALGO), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to decrypt TOTP secret", ex);
        }
    }

    private byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to initialize crypto key", ex);
        }
    }
}