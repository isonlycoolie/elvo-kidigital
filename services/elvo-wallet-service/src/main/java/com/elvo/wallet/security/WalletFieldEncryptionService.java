package com.elvo.wallet.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WalletFieldEncryptionService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.security.encryption");
    private static final String PREFIX = "enc:v1:";

    private final SecretKeySpec keySpec;

    @Autowired
    public WalletFieldEncryptionService(SecretManagerService secretManagerService,
            @Value("${elvo.security.field-encryption.key:}") String configuredKey) {
        this(secretManagerService.resolve(
                "wallet-field-encryption-key",
                configuredKey,
                "ELVO_WALLET_FIELD_ENCRYPTION_KEY",
                "wallet-field-encryption-key-32-bytes!"));
    }

    public WalletFieldEncryptionService(String rawKey) {
        byte[] keyBytes = sha256(rawKey);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        if (plainText.startsWith(PREFIX)) {
            return plainText;
        }

        try {
            byte[] iv = deriveIv(plainText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            AUDIT_LOG.error("wallet_field_encrypt_failed reason={}", ex.getMessage());
            throw new IllegalStateException("Failed to encrypt wallet field", ex);
        }
    }

    public String decrypt(String encryptedValue) {
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
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            AUDIT_LOG.warn("wallet_field_decrypt_failed reason={}", ex.getMessage());
            return encryptedValue;
        }
    }

    private byte[] deriveIv(String value) {
        byte[] digest = sha256("iv:" + value);
        byte[] iv = new byte[12];
        System.arraycopy(digest, 0, iv, 0, iv.length);
        return iv;
    }

    private byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to initialize digest", ex);
        }
    }
}