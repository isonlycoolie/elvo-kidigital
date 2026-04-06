package com.elvo.wallet.audit;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.wallet.security.SecretManagerService;

@Service
public class AuditEventSignatureService {

    private final String signatureSecret;

    public AuditEventSignatureService(SecretManagerService secretManagerService,
                                      @Value("${elvo.security.audit.signature-secret:}") String configuredSecret) {
        this.signatureSecret = requireSecret(secretManagerService.resolve(
                "wallet-audit-signature-secret",
                configuredSecret,
                "ELVO_AUDIT_SIGNATURE_SECRET",
                null));
    }

    public String sign(String eventType, String requestId, String correlationId, Instant occurredAt, String payload) {
        String source = String.join("|",
                safe(eventType),
                safe(requestId),
                safe(correlationId),
                occurredAt == null ? "" : occurredAt.toString(),
                safe(payload));
        return hmacSha256(source);
    }

    public String attachSignature(String payload, String signature) {
        return "sig=" + safe(signature) + ";payload=" + safe(payload);
    }

    private String hmacSha256(String source) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign audit event", ex);
        }
    }

    private String requireSecret(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required secret: elvo.security.audit.signature-secret");
        }
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
