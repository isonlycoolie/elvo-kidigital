package com.elvo.billing.statemachine;

import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.service.event.BillingEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class BillingCompensationHandler {

    private final BillingStateTransitionHandlers stateTransitionHandlers;
    private final BillingEventPublisher billingEventPublisher;

    public BillingCompensationHandler(
            BillingStateTransitionHandlers stateTransitionHandlers,
            BillingEventPublisher billingEventPublisher) {
        this.stateTransitionHandlers = stateTransitionHandlers;
        this.billingEventPublisher = billingEventPublisher;
    }

    public PaymentResponseDto compensateFailedTransaction(BillPayment payment, String reason) {
        if (payment == null) {
            throw new IllegalArgumentException("payment is required for compensation");
        }

        PaymentStatus fromStatus = payment.getStatus();
        if (fromStatus == PaymentStatus.REVERSED) {
            return buildResponse(payment, "payment already reversed");
        }

        String metadata = "{\"reason\":\"" + escapeJson(reason) + "\",\"status\":\"REVERSED\"}";
        stateTransitionHandlers.handleDatabaseUpdate(
                payment,
                fromStatus,
                PaymentStatus.REVERSED,
                "PAYMENT_COMPENSATED",
                reason,
                metadata);

        billingEventPublisher.publishTransactionCompleted(payment.getRequestId(), metadata);
        return buildResponse(payment, reason);
    }

    private static PaymentResponseDto buildResponse(BillPayment payment, String message) {
        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(PaymentStatus.REVERSED);
        response.setExternalReference(payment.getExternalReference());
        response.setReceiptNumber(payment.getReceiptNumber());
        response.setPaidAmount(payment.getPaidAmount() == null ? payment.getAmount() : payment.getPaidAmount());
        response.setCurrency(payment.getCurrency());
        response.setMessage(message == null || message.isBlank() ? "payment reversed" : message);
        response.setMetadata("{\"status\":\"REVERSED\"}");
        return response;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "compensation";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
