package com.elvo.billing.service.orchestration;

import java.util.UUID;

import com.elvo.billing.audit.PaymentAuditLogger;
import com.elvo.billing.client.BillingAdapter;
import com.elvo.billing.client.ProviderResolver;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.PaymentHistory;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.monitoring.BillingMetricsRecorder;
import com.elvo.billing.monitoring.SentryBreadcrumbLogger;
import com.elvo.billing.monitoring.SentryErrorCapture;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.repository.PaymentHistoryRepository;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.service.impl.IdempotencyEnforcer;
import com.elvo.billing.validator.UtilityPaymentValidator;
import org.springframework.stereotype.Component;

@Component
public class PaymentFlow {

    private final UtilityPaymentValidator validator;
    private final ProviderResolver providerResolver;
    private final BillPaymentRepository billPaymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final BillingEventPublisher billingEventPublisher;
    private final IdempotencyEnforcer idempotencyEnforcer;
    private final PaymentAuditLogger paymentAuditLogger;
    private final BillingMetricsRecorder billingMetricsRecorder;
    private final SentryErrorCapture sentryErrorCapture;
    private final SentryBreadcrumbLogger sentryBreadcrumbLogger;

    public PaymentFlow(
            UtilityPaymentValidator validator,
            ProviderResolver providerResolver,
            BillPaymentRepository billPaymentRepository,
            PaymentHistoryRepository paymentHistoryRepository,
            BillingEventPublisher billingEventPublisher,
            IdempotencyEnforcer idempotencyEnforcer,
            PaymentAuditLogger paymentAuditLogger,
            BillingMetricsRecorder billingMetricsRecorder,
            SentryErrorCapture sentryErrorCapture,
            SentryBreadcrumbLogger sentryBreadcrumbLogger) {
        this.validator = validator;
        this.providerResolver = providerResolver;
        this.billPaymentRepository = billPaymentRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.billingEventPublisher = billingEventPublisher;
        this.idempotencyEnforcer = idempotencyEnforcer;
        this.paymentAuditLogger = paymentAuditLogger;
        this.billingMetricsRecorder = billingMetricsRecorder;
        this.sentryErrorCapture = sentryErrorCapture;
        this.sentryBreadcrumbLogger = sentryBreadcrumbLogger;
    }

    public PaymentResponseDto execute(
            UtilityPaymentRequestDto paymentRequest,
            BillCategory billCategory,
            String serviceCode,
            String requestId,
            String correlationId,
            String idempotencyKey,
            UUID userId,
            UUID walletId) {
        long startNanos = System.nanoTime();
        sentryBreadcrumbLogger.addPaymentBreadcrumb("validation", paymentRequest.getReferenceNumber(), serviceCode);
        validator.validateForPayment(paymentRequest, billCategory);

        String normalizedIdempotencyKey = normalizeRequestValue(idempotencyKey);
        String requestHash = serviceCode + "|" + paymentRequest.getReferenceNumber() + "|" + paymentRequest.getAmount();
        idempotencyEnforcer.assertNotProcessed(normalizedIdempotencyKey, "PAYMENT_EXECUTE", requestHash);

        BillingAdapter adapter = providerResolver.resolve(serviceCode);
    sentryBreadcrumbLogger.addPaymentBreadcrumb("execution", paymentRequest.getReferenceNumber(), serviceCode);
        PaymentResponseDto adapterResponse;
        try {
            adapterResponse = adapter.pay(paymentRequest);
        } catch (RuntimeException ex) {
            sentryErrorCapture.capturePaymentFailure(serviceCode, paymentRequest.getReferenceNumber(), ex);
            billingMetricsRecorder.recordPaymentOutcome(PaymentStatus.FAILED, System.nanoTime() - startNanos);
            throw ex;
        }

        BillPayment payment = new BillPayment();
        payment.setPaymentId(adapterResponse.getPaymentId() != null ? adapterResponse.getPaymentId() : UUID.randomUUID());
        payment.setRequestId(normalizeRequestValue(requestId));
        payment.setCorrelationId(normalizeRequestValue(correlationId));
        payment.setIdempotencyKey(normalizedIdempotencyKey);
        payment.setUserId(userId);
        payment.setWalletId(walletId);
        payment.setBillCategory(billCategory);
        payment.setServiceCode(serviceCode);
        payment.setReferenceNumber(paymentRequest.getReferenceNumber());
        payment.setAmount(paymentRequest.getAmount());
        payment.setCurrency(adapterResponse.getCurrency() == null ? "TZS" : adapterResponse.getCurrency());
        payment.setCustomerPhone(paymentRequest.getCustomerPhone());
        payment.setCustomerName(paymentRequest.getCustomerName());
        payment.setMetadata(paymentRequest.getMetadata());
        payment.setStatus(adapterResponse.getStatus() == null ? PaymentStatus.FAILED : adapterResponse.getStatus());
        payment.setExternalReference(adapterResponse.getExternalReference());
        payment.setReceiptNumber(adapterResponse.getReceiptNumber());
        payment.setPaidAmount(adapterResponse.getPaidAmount());
        payment.setCompletedAt(adapterResponse.getCompletedAt());
        billPaymentRepository.save(payment);

        PaymentHistory history = new PaymentHistory();
        history.setPaymentId(payment.getPaymentId());
        history.setRequestId(payment.getRequestId());
        history.setCorrelationId(payment.getCorrelationId());
        history.setEventType("PAYMENT_EXECUTED");
        history.setFromStatus("PENDING");
        history.setToStatus(payment.getStatus().name());
        history.setAdapterName(serviceCode);
        history.setAdapterReference(adapterResponse.getExternalReference());
        history.setResponseCode(payment.getStatus().name());
        history.setResponseMessage(adapterResponse.getMessage());
        history.setMetadata(adapterResponse.getMetadata() == null ? "{}" : adapterResponse.getMetadata());
        paymentHistoryRepository.save(history);
        paymentAuditLogger.logUpdate(payment, "PAYMENT_EXECUTED", "PENDING", payment.getStatus().name());

        billingEventPublisher.publish("billing.payment.completed", payment.getRequestId(), adapterResponse.getMetadata(), "v1");
        idempotencyEnforcer.markProcessed(
                normalizedIdempotencyKey,
                "PAYMENT_EXECUTE",
                requestHash,
                adapterResponse.getMetadata() == null ? "{}" : adapterResponse.getMetadata());

        if (adapterResponse.getPaymentId() == null) {
            adapterResponse.setPaymentId(payment.getPaymentId());
        }
        sentryBreadcrumbLogger.addPaymentBreadcrumb("completed", payment.getReferenceNumber(), serviceCode);
        billingMetricsRecorder.recordPaymentOutcome(payment.getStatus(), System.nanoTime() - startNanos);
        billingMetricsRecorder.recordPendingPayments(billPaymentRepository.countByStatus(PaymentStatus.PENDING));
        return adapterResponse;
    }

    private static String normalizeRequestValue(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }
}
