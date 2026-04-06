package com.elvo.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.identity.entity.VerificationOtp;

@Service
public class OtpCryptoService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final int OTP_MIN = 100_000;
    private static final int OTP_BOUND = 900_000;

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] otpPepper;

    public OtpCryptoService(@Value("${elvo.security.otp.hash-pepper:change-this-otp-pepper}") String otpPepper) {
        this.otpPepper = otpPepper.getBytes(StandardCharsets.UTF_8);
    }

    public String generateSixDigitCode() {
        int value = OTP_MIN + secureRandom.nextInt(OTP_BOUND);
        return Integer.toString(value);
    }

    public String hashOtp(UUID userId,
                          VerificationOtp.Purpose purpose,
                          String destination,
                          String otpCode,
                          String requestId) {
        String normalizedDestination = destination == null ? "" : destination.trim().toLowerCase();
        String payload = String.join("|",
                userId.toString(),
                purpose.name(),
                normalizedDestination,
                requestId == null ? "" : requestId,
                otpCode);

        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(otpPepper, HMAC_SHA_256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash OTP", ex);
        }
    }

    public boolean matches(UUID userId,
                           VerificationOtp.Purpose purpose,
                           String destination,
                           String requestId,
                           String providedOtp,
                           String storedHash) {
        if (providedOtp == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String computedHash = hashOtp(userId, purpose, destination, providedOtp.trim(), requestId);
        return computedHash.equals(storedHash);
    }
}
