package com.elvo.billing.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
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
import com.elvo.billing.monitoring.BillingMetricsRecorder;
import com.elvo.billing.monitoring.SecurityMonitoringService;
import com.elvo.billing.monitoring.SentryBreadcrumbLogger;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.security.BillingFraudDetectionService;
import com.elvo.billing.security.BillingRoleBasedAccessControl;
import com.elvo.billing.security.BillingSensitivePermission;
import com.elvo.billing.service.BillingService;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.service.orchestration.LookupFlow;
import com.elvo.billing.service.orchestration.PaymentFlow;
import com.elvo.billing.service.settlement.BillingWalletSettlementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final BillingMetricsRecorder billingMetricsRecorder;
    private final SentryBreadcrumbLogger sentryBreadcrumbLogger;
    private final BillingRoleBasedAccessControl roleBasedAccessControl;
    private final SecurityMonitoringService securityMonitoringService;
    private final BillingFraudDetectionService billingFraudDetectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private BillingWalletSettlementService walletSettlementService;

    @Value("${elvo.billing.wallet.settlement-enabled:true}")
    private boolean walletSettlementEnabled;

    @Autowired
    public BillingServiceImpl(
            PaymentFlow paymentFlow,
            LookupFlow lookupFlow,
            BillPaymentRepository billPaymentRepository,
            BillingEventPublisher billingEventPublisher,
            PaymentAuditLogger paymentAuditLogger,
            BillingMetricsRecorder billingMetricsRecorder,
            SentryBreadcrumbLogger sentryBreadcrumbLogger,
            BillingRoleBasedAccessControl roleBasedAccessControl) {
        this(paymentFlow, lookupFlow, billPaymentRepository, billingEventPublisher, paymentAuditLogger,
            billingMetricsRecorder, sentryBreadcrumbLogger, roleBasedAccessControl, null, null);
    }

    public BillingServiceImpl(
            PaymentFlow paymentFlow,
            LookupFlow lookupFlow,
            BillPaymentRepository billPaymentRepository,
            BillingEventPublisher billingEventPublisher,
            PaymentAuditLogger paymentAuditLogger,
            BillingMetricsRecorder billingMetricsRecorder,
            SentryBreadcrumbLogger sentryBreadcrumbLogger,
            BillingRoleBasedAccessControl roleBasedAccessControl,
            @Nullable SecurityMonitoringService securityMonitoringService) {
        this(
            paymentFlow, lookupFlow, billPaymentRepository, billingEventPublisher, paymentAuditLogger,
            billingMetricsRecorder, sentryBreadcrumbLogger, roleBasedAccessControl, securityMonitoringService,
            null);
    }

    public BillingServiceImpl(
            PaymentFlow paymentFlow,
            LookupFlow lookupFlow,
            BillPaymentRepository billPaymentRepository,
            BillingEventPublisher billingEventPublisher,
            PaymentAuditLogger paymentAuditLogger,
            BillingMetricsRecorder billingMetricsRecorder,
            SentryBreadcrumbLogger sentryBreadcrumbLogger,
            BillingRoleBasedAccessControl roleBasedAccessControl,
            @Nullable SecurityMonitoringService securityMonitoringService,
            @Nullable BillingFraudDetectionService billingFraudDetectionService) {
        this.paymentFlow = paymentFlow;
        this.lookupFlow = lookupFlow;
        this.billPaymentRepository = billPaymentRepository;
        this.billingEventPublisher = billingEventPublisher;
        this.paymentAuditLogger = paymentAuditLogger;
        this.billingMetricsRecorder = billingMetricsRecorder;
        this.sentryBreadcrumbLogger = sentryBreadcrumbLogger;
        this.roleBasedAccessControl = roleBasedAccessControl;
        this.securityMonitoringService = securityMonitoringService;
        this.billingFraudDetectionService = billingFraudDetectionService;
    }

    @Override
    public PaymentResponseDto createPayment(UtilityPaymentRequestDto paymentRequest) {
        if (paymentRequest == null) {
            throw new PaymentValidationException("request body is required");
        }

        if (billingFraudDetectionService != null) {
            billingFraudDetectionService.analyzePaymentAttempt(paymentRequest);
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
        billingMetricsRecorder.recordPendingPayments(billPaymentRepository.countByStatus(PaymentStatus.PENDING));
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
        if (paymentRequest == null) {
            throw new PaymentValidationException("request body is required");
        }

        PaymentActor actor = resolvePaymentActor(paymentRequest);
        String reference = paymentRequest.getReferenceNumber();
        String idempotencyBase = "bill:" + reference;

        UUID walletId = actor.walletId();
        UUID reservationId = null;
        if (isWalletSettlementEnabled()) {
            BillingWalletSettlementService.WalletReservation reservation = walletSettlementService.reserve(
                    actor.userId(),
                    paymentRequest.getAmount(),
                    idempotencyBase + ":reserve");
            walletId = reservation.walletId();
            reservationId = reservation.reservationId();
            paymentRequest.setMetadata(enrichMetadata(paymentRequest.getMetadata(), actor.userId(), walletId, reservationId));
        }

        try {
            PaymentResponseDto response = paymentFlow.execute(
                    paymentRequest,
                    resolveBillCategory(paymentRequest),
                    resolveServiceCode(paymentRequest),
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    actor.userId(),
                    walletId);

            if (isWalletSettlementEnabled() && reservationId != null && response.getStatus() != null) {
                if (response.getStatus() == PaymentStatus.SUCCESS) {
                    walletSettlementService.confirm(actor.userId(), reservationId, idempotencyBase + ":confirm");
                } else if (response.getStatus() == PaymentStatus.FAILED || response.getStatus() == PaymentStatus.REVERSED) {
                    walletSettlementService.release(actor.userId(), reservationId, idempotencyBase + ":release");
                }
            }

            return response;
        } catch (RuntimeException ex) {
            if (isWalletSettlementEnabled() && reservationId != null) {
                try {
                    walletSettlementService.release(actor.userId(), reservationId, idempotencyBase + ":release");
                } catch (RuntimeException releaseEx) {
                    // Preserve original payment failure while best-effort releasing reserved funds.
                }
            }
            throw ex;
        }
    }

    @Override
    public PaymentResponseDto reversePayment(UtilityPaymentRequestDto reversalRequest) {
        roleBasedAccessControl.authorize(BillingSensitivePermission.PAYMENT_REVERSE);

        String referenceNumber = reversalRequest.getReferenceNumber();
        if (securityMonitoringService != null) {
            securityMonitoringService.recordRepeatedReversalAttempt(referenceNumber);
        }
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
        billingEventPublisher.publish("billing.payment.reversed", payment.getRequestId(), response.getMetadata(), "v1");
        billingMetricsRecorder.recordPendingPayments(billPaymentRepository.countByStatus(PaymentStatus.PENDING));
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

        sentryBreadcrumbLogger.addCallbackBreadcrumb("received", callback.getReferenceNumber(), callback.getStatus());

        BillPayment payment = billPaymentRepository.getPaymentByReferenceWithLock(callback.getReferenceNumber())
                .orElseThrow(() -> new PaymentValidationException("payment not found for referenceNumber " + callback.getReferenceNumber()));

        PaymentSettlementContext settlementContext = resolveSettlementContext(payment.getMetadata());
        String callbackIdempotencyBase = "bill:" + callback.getReferenceNumber();

        PaymentStatus callbackStatus = resolvePaymentStatus(callback.getStatus());
        billPaymentRepository.updatePaymentStatus(payment.getPaymentId(), callbackStatus);
        if (billingFraudDetectionService != null) {
            billingFraudDetectionService.recordCallbackOutcome(callback.getReferenceNumber(), callback.getStatus());
        }

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

        if (isWalletSettlementEnabled() && settlementContext != null) {
            if (callbackStatus == PaymentStatus.SUCCESS) {
                walletSettlementService.confirm(settlementContext.userId(), settlementContext.reservationId(), callbackIdempotencyBase + ":confirm");
            } else if (callbackStatus == PaymentStatus.FAILED || callbackStatus == PaymentStatus.REVERSED) {
                walletSettlementService.release(settlementContext.userId(), settlementContext.reservationId(), callbackIdempotencyBase + ":release");
            }
        }

        paymentAuditLogger.logCallback(payment, callback);
        billingEventPublisher.publish("billing.payment.callback.received", payment.getRequestId(), response.getMetadata(), "v1");
        billingMetricsRecorder.recordPendingPayments(billPaymentRepository.countByStatus(PaymentStatus.PENDING));
        sentryBreadcrumbLogger.addCallbackBreadcrumb("processed", callback.getReferenceNumber(), callback.getStatus());
        return response;
    }

    private PaymentActor resolvePaymentActor(UtilityPaymentRequestDto paymentRequest) {
        UUID userId = resolveUserIdFromMetadata(paymentRequest.getMetadata());
        if (userId == null) {
            userId = resolveUserIdFromSecurityContext();
        }
        if (userId == null) {
            throw new PaymentValidationException("userId is required in metadata or authenticated principal");
        }

        UUID walletId = resolveWalletIdFromMetadata(paymentRequest.getMetadata());
        return new PaymentActor(userId, walletId);
    }

    private UUID resolveUserIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UUID resolveUserIdFromMetadata(String metadata) {
        Map<String, Object> parsed = parseMetadata(metadata);
        Object value = parsed.get("userId");
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            throw new PaymentValidationException("metadata.userId must be a valid UUID");
        }
    }

    private UUID resolveWalletIdFromMetadata(String metadata) {
        Map<String, Object> parsed = parseMetadata(metadata);
        Object value = parsed.get("walletId");
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException ex) {
            throw new PaymentValidationException("metadata.walletId must be a valid UUID");
        }
    }

    private PaymentSettlementContext resolveSettlementContext(String metadata) {
        Map<String, Object> parsed = parseMetadata(metadata);
        Object userId = parsed.get("userId");
        Object reservationId = parsed.get("walletReservationId");
        if (userId == null || reservationId == null) {
            return null;
        }

        try {
            return new PaymentSettlementContext(
                    UUID.fromString(String.valueOf(userId).trim()),
                    UUID.fromString(String.valueOf(reservationId).trim()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String enrichMetadata(String metadata, UUID userId, UUID walletId, UUID reservationId) {
        Map<String, Object> merged = new LinkedHashMap<>(parseMetadata(metadata));
        merged.put("userId", userId);
        if (walletId != null) {
            merged.put("walletId", walletId);
        }
        if (reservationId != null) {
            merged.put("walletReservationId", reservationId);
        }
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException ex) {
            throw new PaymentValidationException("Unable to serialize payment metadata", ex);
        }
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException ex) {
            throw new PaymentValidationException("metadata must be valid JSON", ex);
        }
    }

    private boolean isWalletSettlementEnabled() {
        return walletSettlementEnabled && walletSettlementService != null;
    }

    private record PaymentActor(UUID userId, UUID walletId) {
    }

    private record PaymentSettlementContext(UUID userId, UUID reservationId) {
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
