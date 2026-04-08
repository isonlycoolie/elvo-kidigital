package com.elvo.billing.controller;

import com.elvo.billing.dto.request.ProviderCallbackDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.exception.RateLimitExceededException;
import com.elvo.billing.security.BillingOperationRateLimitService;
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

@RestController
@RequestMapping("/api/v1/internal/bill-payments")
public class ProviderCallbackController {

    private final BillingService billingService;
    private BillingOperationRateLimitService operationRateLimitService;

    public ProviderCallbackController(BillingService billingService) {
        this.billingService = billingService;
    }

    @Autowired(required = false)
    void setOperationRateLimitService(@Nullable BillingOperationRateLimitService operationRateLimitService) {
        this.operationRateLimitService = operationRateLimitService;
    }

    @PostMapping("/provider-callback")
    public ResponseEntity<PaymentResponseDto> handleProviderCallback(
            @Valid @RequestBody ProviderCallbackDto callback) {
        ITransaction transaction = Sentry.startTransaction("api.bill-payments.provider-callback", "http.server");
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
