package com.elvo.billing.audit;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.elvo.billing.dto.request.ProviderCallbackDto;
import com.elvo.billing.entity.BillPayment;

@Component
public class PaymentAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.payment");
    private final ImmutableAuditStorageService immutableAuditStorageService;

    public PaymentAuditLogger(@Nullable ImmutableAuditStorageService immutableAuditStorageService) {
        this.immutableAuditStorageService = immutableAuditStorageService;
    }

    public void logCreate(BillPayment payment) {
        auditLog.info(
                "payment_create paymentId={} requestId={} referenceNumber={} status={} amount={} currency={}",
                payment.getPaymentId(),
                payment.getRequestId(),
                payment.getReferenceNumber(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency());
        appendImmutable("billing.payment.create", payment, "status=" + payment.getStatus());
    }

    public void logUpdate(BillPayment payment, String eventType, String fromStatus, String toStatus) {
        auditLog.info(
                "payment_update eventType={} paymentId={} requestId={} referenceNumber={} fromStatus={} toStatus={}",
                eventType,
                payment.getPaymentId(),
                payment.getRequestId(),
                payment.getReferenceNumber(),
                fromStatus,
                toStatus);
        appendImmutable("billing.payment.update", payment,
            "eventType=" + eventType + ",fromStatus=" + fromStatus + ",toStatus=" + toStatus);
    }

    public void logReverse(BillPayment payment) {
        auditLog.info(
                "payment_reverse paymentId={} requestId={} referenceNumber={} status={}",
                payment.getPaymentId(),
                payment.getRequestId(),
                payment.getReferenceNumber(),
                payment.getStatus());
        appendImmutable("billing.payment.reverse", payment, "status=" + payment.getStatus());
    }

    public void logCallback(BillPayment payment, ProviderCallbackDto callback) {
        auditLog.info(
                "payment_callback paymentId={} requestId={} referenceNumber={} callbackStatus={} externalReference={}",
                payment.getPaymentId(),
                payment.getRequestId(),
                payment.getReferenceNumber(),
                callback.getStatus(),
                callback.getExternalReference());
        appendImmutable("billing.payment.callback", payment,
                "callbackStatus=" + callback.getStatus() + ",externalReference=" + callback.getExternalReference());
    }

    private void appendImmutable(String eventType, BillPayment payment, String payload) {
        if (immutableAuditStorageService == null) {
            return;
        }
        try {
            immutableAuditStorageService.append(
                    eventType,
                    payment.getRequestId(),
                    payment.getCorrelationId(),
                    Instant.now(),
                    payload);
        } catch (RuntimeException ignored) {
            // Preserve billing transaction flow when audit persistence is temporarily unavailable.
        }
    }
}