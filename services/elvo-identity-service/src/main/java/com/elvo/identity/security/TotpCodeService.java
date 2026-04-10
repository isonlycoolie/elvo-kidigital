package com.elvo.identity.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TotpCodeService {

    private static final String HMAC_SHA1 = "HmacSHA1";

    private final int periodSeconds;
    private final int digits;
    private final int allowedSkewSteps;

    public TotpCodeService(
            @Value("${elvo.security.totp.period-seconds:30}") int periodSeconds,
            @Value("${elvo.security.totp.digits:6}") int digits,
            @Value("${elvo.security.totp.allowed-skew-steps:1}") int allowedSkewSteps) {
        this.periodSeconds = periodSeconds;
        this.digits = digits;
        this.allowedSkewSteps = allowedSkewSteps;
    }

    public boolean verify(String base32Secret, String code, Instant atTime) {
        if (base32Secret == null || base32Secret.isBlank() || code == null || !code.matches("\\d{" + digits + "}")) {
            return false;
        }
        long counter = timeCounter(atTime);
        for (int offset = -allowedSkewSteps; offset <= allowedSkewSteps; offset++) {
            String expected = generateAtCounter(base32Secret, counter + offset);
            if (expected.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public String generateAtCounter(String base32Secret, long counter) {
        byte[] secret = Base32Codec.decode(base32Secret);
        byte[] challenge = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(secret, HMAC_SHA1));
            hash = mac.doFinal(challenge);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to generate TOTP", ex);
        }
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int mod = (int) Math.pow(10, digits);
        int otp = binary % mod;
        return String.format("%0" + digits + "d", otp);
    }

    private long timeCounter(Instant atTime) {
        return atTime.getEpochSecond() / Math.max(1, periodSeconds);
    }
}