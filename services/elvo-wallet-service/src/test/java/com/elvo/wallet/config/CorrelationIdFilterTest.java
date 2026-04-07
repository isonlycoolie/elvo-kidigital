package com.elvo.wallet.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class CorrelationIdFilterTest {

    private static final String SECRET = "wallet-correlation-test-secret";

    @Test
    void internalRequestWithInvalidSignatureShouldBeRejected() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/wallets/u1/balance");
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "req-1");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-1");
        request.addHeader(CorrelationIdFilter.CORRELATION_SIGNATURE_HEADER, "invalid-signature");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, noopChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Invalid correlation signature");
    }

    @Test
    void internalRequestWithValidSignatureShouldProceed() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/wallets/u1/balance");
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "req-2");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-2");
        request.addHeader(CorrelationIdFilter.IDEMPOTENCY_KEY_HEADER, "idem-2");
        request.addHeader(CorrelationIdFilter.CORRELATION_SIGNATURE_HEADER, sign("req-2", "corr-2"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean invoked = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_SIGNATURE_HEADER)).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.IDEMPOTENCY_KEY_HEADER)).isEqualTo("idem-2");
    }

    private FilterChain noopChain() {
        return (request, response) -> { };
    }

    private String sign(String requestId, String correlationId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((requestId + "|" + correlationId).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
