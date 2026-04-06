package com.elvo.wallet.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class MobileMoneyCallbackSecurityServiceTest {

    @Test
    void shouldAcceptValidCallbackPayload() {
        MobileMoneyCallbackSecurityService service = new MobileMoneyCallbackSecurityService(
                "mobile-secret",
                300,
                60,
                "10.1.1.8");

        String callbackReference = "cb-1";
        long timestamp = Instant.now().getEpochSecond();
        String sourceIp = "10.1.1.8";
        String signature = sign("mobile-secret", callbackReference, timestamp, sourceIp);

        assertThat(service.isAuthenticatedCallback(callbackReference, signature, timestamp, sourceIp)).isTrue();
    }

    @Test
    void shouldRejectCallbackFromUnknownSourceIp() {
        MobileMoneyCallbackSecurityService service = new MobileMoneyCallbackSecurityService(
                "mobile-secret",
                300,
                60,
                "10.1.1.8");

        String callbackReference = "cb-2";
        long timestamp = Instant.now().getEpochSecond();
        String sourceIp = "10.1.1.9";
        String signature = sign("mobile-secret", callbackReference, timestamp, sourceIp);

        assertThat(service.isAuthenticatedCallback(callbackReference, signature, timestamp, sourceIp)).isFalse();
    }

    @Test
    void shouldRejectExpiredCallbackTimestamp() {
        MobileMoneyCallbackSecurityService service = new MobileMoneyCallbackSecurityService(
                "mobile-secret",
                120,
                60,
                "");

        String callbackReference = "cb-3";
        long timestamp = Instant.now().minusSeconds(180).getEpochSecond();
        String sourceIp = "10.1.1.10";
        String signature = sign("mobile-secret", callbackReference, timestamp, sourceIp);

        assertThat(service.isAuthenticatedCallback(callbackReference, signature, timestamp, sourceIp)).isFalse();
    }

    private String sign(String secret, String callbackReference, long timestamp, String sourceIp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = callbackReference + "|" + timestamp + "|" + sourceIp;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
