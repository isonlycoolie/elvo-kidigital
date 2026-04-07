package com.elvo.billing.service.impl;

import java.util.UUID;

import com.elvo.billing.audit.PaymentAuditLogger;
import com.elvo.billing.dto.request.ProviderCallbackDto;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.exception.DuplicatePaymentException;
import com.elvo.billing.exception.PaymentValidationException;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.service.BillingService;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.service.orchestration.LookupFlow;
import com.elvo.billing.service.orchestration.PaymentFlow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BillingServiceImpl implements BillingService {

    private final PaymentFlow paymentFlow;
    private final LookupFlow lookupFlow;
    private final BillPaymentRepository billPaymentRepository;
    private final BillingEventPublisher billingEventPublisher;
    private final PaymentAuditLogger paymentAuditLogger;

    public BillingServiceImpl(
            PaymentFlow paymentFlow,
            LookupFlow lookupFlow,
            BillPaymentRepository billPaymentRepository,
            BillingEventPublisher billingEventPublisher,
            PaymentAuditLogger paymentAuditLogger) {
        this.paymentFlow = paymentFlow;
        this.lookupFlow = lookupFlow;
        this.billPaymentRepository = billPaymentRepository;
        this.billingEventPublisher = billingEventPublisher;
        this.paymentAuditLogger = paymentAuditLogger;
    }

    @Override
    public PaymentResponseDto createPayment(UtilityPaymentRequestDto paymentRequest) {
        if (paymentRequest == null) {
            throw new PaymentValidationException("request body is required");
        }

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId(UUID.randomUUID().toString());
        payment.setCorrelationId(UUID.randomUUID().toString());
        payment.setIdempotencyKey(UUID.randomUUID().toString());
        payment.setUserId(UUID.randomUUID());
        payment.setWalletId(UUID.randomUUID());
        payment.setBillCategory(resolveBillCategory(paymentRequest));
        payment.setServiceCode(resolveServiceCode(paymentRequest));
        payment.setReferenceNumber(paymentRequest.getReferenceNumber());
        payment.setAmount(paymentRequest.getAmount());
        payment.setCurrency("TZS");
        payment.setCustomerPhone(paymentRequest.getCustomerPhone());
        payment.setCustomerName(paymentRequest.getCustomerName());
        payment.setMetadata(paymentRequest.getMetadata());
        payment.setStatus(PaymentStatus.PENDING);

        BillPayment persisted = billPaymentRepository.createPayment(payment);

        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(persisted.getPaymentId());
        response.setStatus(persisted.getStatus());
        response.setPaidAmount(persisted.getAmount());
        response.setCurrency(persisted.getCurrency());
        response.setMetadata(persisted.getMetadata());
        response.setMessage("payment created");
        paymentAuditLogger.logCreate(persisted);
        return response;
    }

    @Override
    public LookupResponseDto lookupPayment(UtilityPaymentRequestDto lookupRequest) {
        return lookupFlow.execute(
                lookupRequest,
                resolveBillCategory(lookupRequest),
                resolveServiceCode(lookupRequest),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
    }

    @Override
    public PaymentResponseDto executePayment(UtilityPaymentRequestDto paymentRequest) {
        return paymentFlow.execute(
                paymentRequest,
                resolveBillCategory(paymentRequest),
                resolveServiceCode(paymentRequest),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    @Override
    public PaymentResponseDto reversePayment(UtilityPaymentRequestDto reversalRequest) {
        String referenceNumber = reversalRequest.getReferenceNumber();
        BillPayment payment = billPaymentRepository.getPaymentByReferenceWithLock(referenceNumber)
                .orElseThrow(() -> new PaymentValidationException("payment not found for referenceNumber"));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new DuplicatePaymentException("payment is not reversible");
        }

        billPaymentRepository.updatePaymentStatus(payment.getPaymentId(), PaymentStatus.REVERSED);

        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(PaymentStatus.REVERSED);
        response.setExternalReference(payment.getExternalReference());
        response.setReceiptNumber(payment.getReceiptNumber());
        response.setPaidAmount(payment.getPaidAmount() == null ? payment.getAmount() : payment.getPaidAmount());
        response.setCurrency(payment.getCurrency());
        response.setMetadata("{\"compensationTriggered\":true,\"paymentId\":\"" + payment.getPaymentId() + "\"}");
        response.setMessage("payment reversed");

        paymentAuditLogger.logReverse(payment);
        billingEventPublisher.publish("billing.payment.reversed", payment.getRequestId(), response.getMetadata());
        return response;
    }

    private static BillCategory resolveBillCategory(UtilityPaymentRequestDto request) {
        String metadata = request.getMetadata();
        if (metadata != null && metadata.toUpperCase().contains("GOVERNMENT")) {
            return BillCategory.GOVERNMENT;
        }
        if (metadata != null && metadata.toUpperCase().contains("WATER")) {
            return BillCategory.WATER;
        }
        return BillCategory.ELECTRICITY;
    }

    private static String resolveServiceCode(UtilityPaymentRequestDto request) {
        String metadata = request.getMetadata();
        if (metadata != null && metadata.toUpperCase().contains("SERVICE_CODE")) {
            return "LUKU";
        }
        return "LUKU";
    }

    @Override
    public PaymentResponseDto findPaymentById(UUID paymentId) {
        if (paymentId == null) {
            throw new PaymentValidationException("paymentId is required");
        }

        BillPayment payment = billPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentValidationException("payment not found for paymentId " + paymentId));

        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus());
        response.setExternalReference(payment.getExternalReference());
        response.setMessage("payment found");
        response.setReceiptNumber(payment.getReceiptNumber());
        response.setPaidAmount(payment.getPaidAmount() == null ? payment.getAmount() : payment.getPaidAmount());
        response.setCurrency(payment.getCurrency());
        response.setCompletedAt(payment.getCompletedAt());
        response.setMetadata(payment.getMetadata());
        return response;
    }

    @Override
    public PaymentResponseDto findPaymentByReference(String referenceNumber) {
        if (referenceNumber == null || referenceNumber.isBlank()) {
            throw new PaymentValidationException("referenceNumber is required");
        }

        BillPayment payment = billPaymentRepository.getPaymentByReference(referenceNumber)
                .orElseThrow(() -> new PaymentValidationException("payment not found for referenceNumber " + referenceNumber));

        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus());
        response.setExternalReference(payment.getExternalReference());
        response.setMessage("payment found");
        response.setReceiptNumber(payment.getReceiptNumber());
        response.setPaidAmount(payment.getPaidAmount() == null ? payment.getAmount() : payment.getPaidAmount());
        response.setCurrency(payment.getCurrency());
        response.setCompletedAt(payment.getCompletedAt());
        response.setMetadata(payment.getMetadata());
        return response;
    }

    @Override
    public PaymentResponseDto handleProviderCallback(ProviderCallbackDto callback) {
        if (callback == null || callback.getReferenceNumber() == null || callback.getReferenceNumber().isBlank()) {
            throw new PaymentValidationException("referenceNumber is required for provider callback");
        }

        BillPayment payment = billPaymentRepository.getPaymentByReferenceWithLock(callback.getReferenceNumber())
                .orElseThrow(() -> new PaymentValidationException("payment not found for referenceNumber " + callback.getReferenceNumber()));

        PaymentStatus callbackStatus = resolvePaymentStatus(callback.getStatus());
        billPaymentRepository.updatePaymentStatus(payment.getPaymentId(), callbackStatus);

        if (callback.getReceiptNumber() != null) {
            payment.setReceiptNumber(callback.getReceiptNumber());
        }
        if (callback.getExternalReference() != null) {
            payment.setExternalReference(callback.getExternalReference());
        }

        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(callbackStatus);
        response.setExternalReference(callback.getExternalReference());
        response.setReceiptNumber(callback.getReceiptNumber());
        response.setMessage("provider callback processed");
        response.setPaidAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setMetadata(callback.getMetadata());

        paymentAuditLogger.logCallback(payment, callback);
        billingEventPublisher.publish("billing.payment.callback.received", payment.getRequestId(), response.getMetadata());
        return response;
    }

    private static PaymentStatus resolvePaymentStatus(String statusString) {
        if (statusString == null || statusString.isBlank()) {
            return PaymentStatus.FAILED;
        }
        try {
            return PaymentStatus.valueOf(statusString.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PaymentStatus.FAILED;
        }
    }
}
