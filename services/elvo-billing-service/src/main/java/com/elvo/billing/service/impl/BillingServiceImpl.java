package com.elvo.billing.service.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.exception.PaymentAlreadyExistsException;
import com.elvo.billing.exception.PaymentAlreadyExecutedException;
import com.elvo.billing.exception.PaymentNotReversibleException;
import com.elvo.billing.exception.PaymentNotFoundException;
import com.elvo.billing.exception.PaymentValidationException;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.service.BillingService;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.service.orchestration.LookupFlow;
import com.elvo.billing.service.orchestration.PaymentFlow;
import com.elvo.billing.validator.UtilityPaymentValidator;

/**
 * Core implementation of the BillingService for payment lifecycle management.
 * 
 * Orchestrates:
 * 1. createPayment: Validate and create a PENDING payment
 * 2. lookupPayment: Adapter lookup without charging
 * 3. executePayment: Charge via adapter and update status
 * 4. reversePayment: Reverse a SUCCESS payment
 * 
 * All operations are transactional to ensure consistency.
 */
@Service
@Transactional
public class BillingServiceImpl implements BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceImpl.class);
    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.service");

    private final BillPaymentRepository paymentRepository;
    private final UtilityPaymentValidator validator;
    private final PaymentFlow paymentFlow;
    private final LookupFlow lookupFlow;
    private final BillingEventPublisher eventPublisher;
    private final IdempotencyEnforcer idempotencyEnforcer;

    public BillingServiceImpl(
            BillPaymentRepository paymentRepository,
            UtilityPaymentValidator validator,
            PaymentFlow paymentFlow,
            LookupFlow lookupFlow,
            BillingEventPublisher eventPublisher,
            IdempotencyEnforcer idempotencyEnforcer) {
        this.paymentRepository = paymentRepository;
        this.validator = validator;
        this.paymentFlow = paymentFlow;
        this.lookupFlow = lookupFlow;
        this.eventPublisher = eventPublisher;
        this.idempotencyEnforcer = idempotencyEnforcer;
    }

    @Override
    public PaymentResponseDto createPayment(UtilityPaymentRequestDto paymentRequest) {
        // Validate the request
        validator.validatePaymentRequest(paymentRequest);
        auditLog.info("Create payment validation passed: referenceNumber={}, serviceCode={}", 
                paymentRequest.getReferenceNumber(), paymentRequest.getServiceCode());

        // Check idempotency: if idempotencyKey already exists, return cached result
        if (paymentRequest.getIdempotencyKey() != null && !paymentRequest.getIdempotencyKey().isBlank()) {
            var cachedResult = idempotencyEnforcer.getCachedResult(paymentRequest.getIdempotencyKey());
            if (cachedResult.isPresent()) {
                auditLog.info("Idempotency cache hit: idempotencyKey={}, returning cached paymentId={}", 
                        paymentRequest.getIdempotencyKey(), cachedResult.get().getPaymentId());
                return cachedResult.get();
            }
        }

        // Create BillPayment entity
        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId(UUID.randomUUID().toString());
        payment.setCorrelationId(paymentRequest.getIdempotencyKey() != null ? paymentRequest.getIdempotencyKey() : UUID.randomUUID().toString());
        payment.setIdempotencyKey(paymentRequest.getIdempotencyKey() != null ? paymentRequest.getIdempotencyKey() : UUID.randomUUID().toString());
        payment.setUserId(UUID.randomUUID()); // TODO: Get from security context
        payment.setWalletId(UUID.randomUUID()); // TODO: Get from request or security context
        payment.setBillCategory(paymentRequest.getBillCategory());
        payment.setServiceCode(paymentRequest.getServiceCode());
        payment.setReferenceNumber(paymentRequest.getReferenceNumber());
        payment.setAmount(paymentRequest.getAmount());
        payment.setCurrency("TZS"); // Default currency
        payment.setCustomerPhone(paymentRequest.getCustomerPhone());
        payment.setCustomerName(paymentRequest.getCustomerName());
        payment.setMetadata(paymentRequest.getMetadata() != null ? paymentRequest.getMetadata() : "{}");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());

        // Persist the payment
        BillPayment persisted = paymentRepository.createPayment(payment);
        auditLog.info("Payment created: paymentId={}, status=PENDING, referenceNumber={}", 
                persisted.getPaymentId(), persisted.getReferenceNumber());

        // Publish event
        eventPublisher.publishPaymentRequested(persisted);

        PaymentResponseDto response = mapToPaymentResponse(persisted);

        // Record idempotency result
        if (paymentRequest.getIdempotencyKey() != null && !paymentRequest.getIdempotencyKey().isBlank()) {
            idempotencyEnforcer.recordResult(paymentRequest.getIdempotencyKey(), persisted.getPaymentId(),
                    "PAYMENT_CREATION", response);
        }

        return response;
    }

    @Override
    public LookupResponseDto lookupPayment(UtilityPaymentRequestDto lookupRequest) {
        String requestId = UUID.randomUUID().toString();
        return lookupFlow.executeLookup(lookupRequest, requestId);
    }

    @Override
    public PaymentResponseDto executePayment(UtilityPaymentRequestDto paymentRequest) {
        // Validate request has payment ID
        if (paymentRequest.getPaymentId() == null) {
            throw new PaymentValidationException("Payment execution requires paymentId");
        }

        // Fetch the payment
        Optional<BillPayment> paymentOpt = paymentRepository.getPaymentById(paymentRequest.getPaymentId());
        if (paymentOpt.isEmpty()) {
            throw new PaymentNotFoundException("Payment not found: " + paymentRequest.getPaymentId());
        }

        BillPayment payment = paymentOpt.get();
        auditLog.info("Payment retrieved: paymentId={}, status={}", payment.getPaymentId(), payment.getStatus());

        // Execute payment and update DB
        return paymentFlow.executePayment(paymentRequest, payment);
    }

    @Override
    public PaymentResponseDto reversePayment(UtilityPaymentRequestDto reversalRequest) {
        // Validate request has payment ID
        if (reversalRequest.getPaymentId() == null) {
            throw new PaymentValidationException("Payment reversal requires paymentId");
        }

        // Fetch the payment
        Optional<BillPayment> paymentOpt = paymentRepository.getPaymentById(reversalRequest.getPaymentId());
        if (paymentOpt.isEmpty()) {
            throw new PaymentNotFoundException("Payment not found for reversal: " + reversalRequest.getPaymentId());
        }

        BillPayment payment = paymentOpt.get();

        // Verify payment is in SUCCESS status
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentNotReversibleException(
                    "Only SUCCESS payments can be reversed; current status: " + payment.getStatus());
        }

        // Update status to REVERSED
        payment.setStatus(PaymentStatus.REVERSED);
        BillPayment updated = paymentRepository.updatePaymentStatus(payment.getPaymentId(), PaymentStatus.REVERSED);
        auditLog.info("Payment reversed: paymentId={}, previousStatus=SUCCESS, newStatus=REVERSED", 
                payment.getPaymentId());

        // Publish reversal event
        eventPublisher.publishPaymentReversed(updated);

        // Return response
        return mapToPaymentResponse(updated);
    }

    private PaymentResponseDto mapToPaymentResponse(BillPayment payment) {
        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(payment.getPaymentId());
        response.setExternalReference(payment.getExternalReference());
        response.setStatus(payment.getStatus());
        response.setMessage(statusMessage(payment.getStatus()));
        response.setReceiptNumber(payment.getReceiptNumber());
        response.setPaidAmount(payment.getPaidAmount());
        response.setCurrency(payment.getCurrency());
        response.setCompletedAt(payment.getCompletedAt());
        response.setMetadata(payment.getMetadata());
        return response;
    }

    private String statusMessage(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "Payment is pending execution";
            case SUCCESS -> "Payment completed successfully";
            case FAILED -> "Payment execution failed";
            case REVERSED -> "Payment has been reversed";
            default -> "Unknown payment status";
        };
    }
}
