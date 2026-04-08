package com.elvo.billing.controller;

import com.elvo.billing.dto.request.ProviderCallbackDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.exception.RateLimitExceededException;
import com.elvo.billing.exception.CallbackVerificationFailedException;
import com.elvo.billing.security.BillingOperationRateLimitService;
import com.elvo.billing.security.CallbackVerificationService;
import com.elvo.billing.service.BillingService;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;


@RestController
@RequestMapping("/api/v1/internal/bill-payments")
public class ProviderCallbackController {

    private final BillingService billingService;
    private BillingOperationRateLimitService operationRateLimitService;
    private CallbackVerificationService callbackVerificationService;

    public ProviderCallbackController(BillingService billingService) {
        this.billingService = billingService;
    }

    private String extractClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp != null && !clientIp.isEmpty()) {
            return clientIp.split(",")[0].trim();
        }
        clientIp = request.getHeader("X-Real-IP");
        if (clientIp != null && !clientIp.isEmpty()) {
            return clientIp;
        }
        return request.getRemoteAddr();
    }

    @Autowired(required = false)
    void setOperationRateLimitService(@Nullable BillingOperationRateLimitService operationRateLimitService) {
        this.operationRateLimitService = operationRateLimitService;
    }

    @Autowired(required = false)
    void setCallbackVerificationService(@Nullable CallbackVerificationService callbackVerificationService) {
        this.callbackVerificationService = callbackVerificationService;
    }

    public ResponseEntity<PaymentResponseDto> handleProviderCallback(@Valid @RequestBody ProviderCallbackDto callback) {
        return handleProviderCallback(callback, null, null, null, "default", null);
    }

    @PostMapping("/provider-callback")
    public ResponseEntity<PaymentResponseDto> handleProviderCallback(
            @Valid @RequestBody ProviderCallbackDto callback,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Provider-Id", required = false, defaultValue = "default") String providerId,
            HttpServletRequest request) {
        ITransaction transaction = Sentry.startTransaction("api.bill-payments.provider-callback", "http.server");
        
                // Verify callback signature, source, timestamp, and replay
                if (callbackVerificationService != null) {
                    try {
                        String payloadJson = new ObjectMapper().writeValueAsString(callback);
                        String sourceIp = extractClientIp(request);
                        callbackVerificationService.verifyCallback(payloadJson, signature, nonce, timestamp, sourceIp, providerId);
                    } catch (CallbackVerificationService.CallbackVerificationException e) {
                        transaction.setThrowable(e);
                        transaction.setStatus(SpanStatus.INTERNAL_ERROR);
                        transaction.finish();
                        throw new CallbackVerificationFailedException(e.getMessage(), e);
                    } catch (Exception e) {
                        transaction.setThrowable(e);
                        transaction.setStatus(SpanStatus.INTERNAL_ERROR);
                        transaction.finish();
                        throw new RuntimeException("Callback verification error: " + e.getMessage(), e);
                    }
                }
        
        enforceRateLimit(
                BillingOperationRateLimitService.Operation.PROVIDER_CALLBACK,
                callback == null ? null : callback.getReferenceNumber(),
                callback == null ? null : callback.getExternalReference());
        try {
            PaymentResponseDto response = billingService.handleProviderCallback(callback);
            transaction.setStatus(SpanStatus.OK);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (RuntimeException ex) {
            transaction.setThrowable(ex);
            transaction.setStatus(SpanStatus.INTERNAL_ERROR);
            throw ex;
        } finally {
            transaction.finish();
        }
    }

    private void enforceRateLimit(BillingOperationRateLimitService.Operation operation, String primaryKey, String secondaryKey) {
        if (operationRateLimitService == null) {
            return;
        }
        BillingOperationRateLimitService.RateLimitResult result = operationRateLimitService.enforce(operation, primaryKey, secondaryKey);
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.reason());
        }
    }
}
