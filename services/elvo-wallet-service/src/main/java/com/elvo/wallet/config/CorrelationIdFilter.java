package com.elvo.wallet.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.elvo.wallet.security.SecretManagerService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_SIGNATURE_HEADER = "X-Correlation-Signature";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";

    private final String correlationSignatureSecret;

    @Autowired
    public CorrelationIdFilter(SecretManagerService secretManagerService,
                               @Value("${elvo.security.correlation.signature-secret:}") String configuredSecret) {
        this(requireSecret(secretManagerService.resolve(
                "wallet-correlation-signature-secret",
                configuredSecret,
                "ELVO_INTERNAL_JWT_SECRET",
            null),
            "elvo.security.correlation.signature-secret"));
    }

    public CorrelationIdFilter(String correlationSignatureSecret) {
        this.correlationSignatureSecret = correlationSignatureSecret;
    }

    private static String requireSecret(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required secret: " + key);
        }
        return value;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingCorrelationId = request.getHeader(CORRELATION_ID_HEADER);
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String correlationId = incomingCorrelationId;
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = requestId;
        }

        if (isInternalRequest(request)
                && incomingCorrelationId != null
                && !incomingCorrelationId.isBlank()) {
            String signature = request.getHeader(CORRELATION_SIGNATURE_HEADER);
            if (!isValidSignature(requestId, correlationId, signature)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"code\":\"ACCESS_DENIED\",\"message\":\"Invalid correlation signature\"}");
                return;
            }
        }

        String propagatedSignature = sign(requestId, correlationId);

        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            MDC.put(IDEMPOTENCY_KEY, idempotencyKey);
        }
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            response.setHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        response.setHeader(CORRELATION_SIGNATURE_HEADER, propagatedSignature);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove(CORRELATION_ID_KEY);
            MDC.remove(IDEMPOTENCY_KEY);
        }
    }

    private boolean isInternalRequest(HttpServletRequest request) {
        return request.getRequestURI() != null && request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    private boolean isValidSignature(String requestId, String correlationId, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        byte[] expected = sign(requestId, correlationId).getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedSignature.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sign(String requestId, String correlationId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(correlationSignatureSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = requestId + "|" + correlationId;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            throw new IllegalStateException("Failed to sign correlation identifiers", ex);
        }
    }
}
