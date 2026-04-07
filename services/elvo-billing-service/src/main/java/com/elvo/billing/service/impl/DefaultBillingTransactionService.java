package com.elvo.billing.service.impl;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.service.BillingTransactionService;
import com.elvo.billing.statemachine.BillingCompensationHandler;
import com.elvo.billing.statemachine.BillingRetryMechanism;
import com.elvo.billing.statemachine.BillingStateTransitionHandlers;
import com.elvo.billing.statemachine.BillingTransactionStateMachine;
import org.springframework.stereotype.Service;

@Service
public class DefaultBillingTransactionService implements BillingTransactionService {

    private final BillingTransactionStateMachine stateMachine;
    private final BillingStateTransitionHandlers stateTransitionHandlers;
    private final BillingRetryMechanism retryMechanism;
    private final BillingCompensationHandler compensationHandler;

    public DefaultBillingTransactionService(
            BillingTransactionStateMachine stateMachine,
            BillingStateTransitionHandlers stateTransitionHandlers,
            BillingRetryMechanism retryMechanism,
            BillingCompensationHandler compensationHandler) {
        this.stateMachine = stateMachine;
        this.stateTransitionHandlers = stateTransitionHandlers;
        this.retryMechanism = retryMechanism;
        this.compensationHandler = compensationHandler;
    }

    @Override
    public PaymentResponseDto initiateTransaction(UtilityPaymentRequestDto paymentRequest, BillCategory billCategory) {
        stateTransitionHandlers.handleValidation(paymentRequest, billCategory);

        PaymentResponseDto response = new PaymentResponseDto();
        response.setStatus(stateMachine.transition(PaymentStatus.INITIATED, PaymentStatus.PENDING));
        response.setMessage("billing transaction initiated");
        return response;
    }

    @Override
    public PaymentResponseDto processTransaction(String serviceCode, UtilityPaymentRequestDto paymentRequest) {
        PaymentResponseDto response = retryMechanism.executePaymentWithRetry(serviceCode, paymentRequest);
        if (response.getStatus() == null) {
            response.setStatus(PaymentStatus.FAILED);
        }
        return response;
    }

    @Override
    public PaymentResponseDto completeTransaction(BillPayment payment) {
        PaymentStatus nextStatus = stateMachine.transition(PaymentStatus.PROCESSING, PaymentStatus.SUCCESS);
        payment.setStatus(nextStatus);

        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(nextStatus);
        response.setExternalReference(payment.getExternalReference());
        response.setReceiptNumber(payment.getReceiptNumber());
        response.setPaidAmount(payment.getPaidAmount() == null ? payment.getAmount() : payment.getPaidAmount());
        response.setCurrency(payment.getCurrency());
        response.setMessage("billing transaction completed");
        return response;
    }

    @Override
    public PaymentResponseDto reverseTransaction(BillPayment payment, String reason) {
        return compensationHandler.compensateFailedTransaction(payment, reason);
    }
}
