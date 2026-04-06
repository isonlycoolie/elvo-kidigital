package com.elvo.wallet.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MobileMoneyCallbackSecurityService {

    private final String callbackSignatureSecret;
    private final long maxCallbackAgeSeconds;
    private final long maxFutureSkewSeconds;
    private final Set<String> allowedSourceIps;

        @Autowired
        public MobileMoneyCallbackSecurityService(
            SecretManagerService secretManagerService,
            @Value("${elvo.mobile.callback.signature-secret:}") String configuredSecret,
            @Value("${elvo.mobile.callback.max-age-seconds:300}") long maxCallbackAgeSeconds,
            @Value("${elvo.mobile.callback.max-future-skew-seconds:60}") long maxFutureSkewSeconds,
            @Value("${elvo.mobile.callback.allowed-source-ips:}") String allowedSourceIps) {
        this(
            secretManagerService.resolve(
                "mobile-callback-signature-secret",
                configuredSecret,
                "ELVO_MOBILE_CALLBACK_SIGNATURE_SECRET",
                null),
            maxCallbackAgeSeconds,
            maxFutureSkewSeconds,
            allowedSourceIps);
        }

        public MobileMoneyCallbackSecurityService(
            String callbackSignatureSecret,
            long maxCallbackAgeSeconds,
            long maxFutureSkewSeconds,
            String allowedSourceIps) {
            if (callbackSignatureSecret == null || callbackSignatureSecret.isBlank()) {
                throw new IllegalStateException("Missing required secret: elvo.mobile.callback.signature-secret");
            }
        this.callbackSignatureSecret = callbackSignatureSecret;
        this.maxCallbackAgeSeconds = maxCallbackAgeSeconds;
        this.maxFutureSkewSeconds = maxFutureSkewSeconds;
        this.allowedSourceIps = Arrays.stream(allowedSourceIps.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    public boolean isAuthenticatedCallback(String callbackReference,
                                           String callbackSignature,
                                           Long callbackTimestamp,
                                           String sourceIp) {
        if (callbackReference == null || callbackReference.isBlank()) {
            return false;
        }
        if (callbackSignature == null || callbackSignature.isBlank() || callbackTimestamp == null) {
            return false;
        }

        String normalizedIp = normalizeIp(sourceIp);
        if (!isAllowedSourceIp(normalizedIp)) {
            return false;
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        long deltaSeconds = nowEpochSeconds - callbackTimestamp;
        if (deltaSeconds > maxCallbackAgeSeconds || deltaSeconds < -maxFutureSkewSeconds) {
            return false;
        }

        String expectedSignature = sign(callbackReference.trim(), callbackTimestamp, normalizedIp);
        byte[] expected = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] provided = callbackSignature.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    private boolean isAllowedSourceIp(String sourceIp) {
        return allowedSourceIps.isEmpty() || allowedSourceIps.contains(sourceIp);
    }

    private String normalizeIp(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) {
            return "unknown";
        }
        String[] addresses = sourceIp.split(",");
        return addresses[0].trim();
    }

    private String sign(String callbackReference, long callbackTimestamp, String sourceIp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSignatureSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = callbackReference + "|" + callbackTimestamp + "|" + sourceIp;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            throw new IllegalStateException("Failed to sign mobile callback payload", ex);
        }
    }
}
