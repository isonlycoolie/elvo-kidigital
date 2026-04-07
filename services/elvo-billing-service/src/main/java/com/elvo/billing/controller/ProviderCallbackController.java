package com.elvo.billing.controller;

import com.elvo.billing.dto.request.ProviderCallbackDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.service.BillingService;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/bill-payments")
public class ProviderCallbackController {

    private final BillingService billingService;

    public ProviderCallbackController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/provider-callback")
    public ResponseEntity<PaymentResponseDto> handleProviderCallback(
            @Valid @RequestBody ProviderCallbackDto callback) {
        ITransaction transaction = Sentry.startTransaction("api.bill-payments.provider-callback", "http.server");
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
}
