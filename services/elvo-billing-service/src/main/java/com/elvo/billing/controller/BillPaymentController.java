package com.elvo.billing.controller;

import java.util.UUID;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.service.BillingService;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bill-payments")
public class BillPaymentController {

    private final BillingService billingService;

    public BillPaymentController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponseDto> createPayment(
            @Valid @RequestBody UtilityPaymentRequestDto request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        ITransaction transaction = Sentry.startTransaction("api.bill-payments.create", "http.server");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            PaymentResponseDto response = billingService.executePayment(request);
            transaction.setStatus(SpanStatus.OK);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException ex) {
            transaction.setThrowable(ex);
            transaction.setStatus(SpanStatus.INTERNAL_ERROR);
            throw ex;
        } finally {
            transaction.finish();
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponseDto> getPayment(@PathVariable UUID paymentId) {
        ITransaction transaction = Sentry.startTransaction("api.bill-payments.get-by-id", "http.server");
        try {
            PaymentResponseDto response = billingService.findPaymentById(paymentId);
            transaction.setStatus(SpanStatus.OK);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            transaction.setThrowable(ex);
            transaction.setStatus(SpanStatus.INTERNAL_ERROR);
            throw ex;
        } finally {
            transaction.finish();
        }
    }

    @GetMapping("/reference/{reference}")
    public ResponseEntity<PaymentResponseDto> getPaymentByReference(@PathVariable String reference) {
        ITransaction transaction = Sentry.startTransaction("api.bill-payments.get-by-reference", "http.server");
        try {
            PaymentResponseDto response = billingService.findPaymentByReference(reference);
            transaction.setStatus(SpanStatus.OK);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            transaction.setThrowable(ex);
            transaction.setStatus(SpanStatus.INTERNAL_ERROR);
            throw ex;
        } finally {
            transaction.finish();
        }
    }

    @PostMapping("/lookup")
    public ResponseEntity<LookupResponseDto> lookupPayment(
            @Valid @RequestBody UtilityPaymentRequestDto request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        ITransaction transaction = Sentry.startTransaction("api.bill-payments.lookup", "http.server");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            LookupResponseDto response = billingService.lookupPayment(request);
            transaction.setStatus(SpanStatus.OK);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            transaction.setThrowable(ex);
            transaction.setStatus(SpanStatus.INTERNAL_ERROR);
            throw ex;
        } finally {
            transaction.finish();
        }
    }
}
