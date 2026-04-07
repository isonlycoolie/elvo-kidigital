package com.elvo.billing.audit;

import com.elvo.billing.dto.request.ProviderCallbackDto;
import com.elvo.billing.entity.BillPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.payment");

    public void logCreate(BillPayment payment) {
        auditLog.info(
                "payment_create paymentId={} requestId={} referenceNumber={} status={} amount={} currency={}",
                payment.getPaymentId(),
                payment.getRequestId(),
                payment.getReferenceNumber(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency());
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
    }

    public void logReverse(BillPayment payment) {
        auditLog.info(
                "payment_reverse paymentId={} requestId={} referenceNumber={} status={}",
                payment.getPaymentId(),
                payment.getRequestId(),
                payment.getReferenceNumber(),
                payment.getStatus());
    }

    public void logCallback(BillPayment payment, ProviderCallbackDto callback) {
        auditLog.info(
                "payment_callback paymentId={} requestId={} referenceNumber={} callbackStatus={} externalReference={}",
                payment.getPaymentId(),
                payment.getRequestId(),
                payment.getReferenceNumber(),
                callback.getStatus(),
                callback.getExternalReference());
    }
}