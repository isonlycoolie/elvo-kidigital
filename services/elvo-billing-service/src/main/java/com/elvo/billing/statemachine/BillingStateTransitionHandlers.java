package com.elvo.billing.statemachine;

import com.elvo.billing.client.BillingAdapter;
import com.elvo.billing.client.ProviderResolver;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.PaymentHistory;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.repository.PaymentHistoryRepository;
import com.elvo.billing.validator.UtilityPaymentValidator;
import org.springframework.stereotype.Component;

@Component
public class BillingStateTransitionHandlers {

    private final UtilityPaymentValidator utilityPaymentValidator;
    private final ProviderResolver providerResolver;
    private final BillPaymentRepository billPaymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    public BillingStateTransitionHandlers(
            UtilityPaymentValidator utilityPaymentValidator,
            ProviderResolver providerResolver,
            BillPaymentRepository billPaymentRepository,
            PaymentHistoryRepository paymentHistoryRepository) {
        this.utilityPaymentValidator = utilityPaymentValidator;
        this.providerResolver = providerResolver;
        this.billPaymentRepository = billPaymentRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
    }

    public void handleValidation(UtilityPaymentRequestDto paymentRequest, BillCategory billCategory) {
        utilityPaymentValidator.validateForPayment(paymentRequest, billCategory);
    }

    public PaymentResponseDto handleWalletCall(String serviceCode, UtilityPaymentRequestDto paymentRequest) {
        BillingAdapter adapter = providerResolver.resolve(serviceCode);
        return adapter.pay(paymentRequest);
    }

    public void handleDatabaseUpdate(
            BillPayment payment,
            PaymentStatus fromStatus,
            PaymentStatus toStatus,
            String eventType,
            String responseMessage,
            String metadata) {
        payment.setStatus(toStatus);
        billPaymentRepository.save(payment);

        PaymentHistory history = new PaymentHistory();
        history.setPaymentId(payment.getPaymentId());
        history.setRequestId(payment.getRequestId());
        history.setCorrelationId(payment.getCorrelationId());
        history.setEventType(eventType);
        history.setFromStatus(fromStatus == null ? null : fromStatus.name());
        history.setToStatus(toStatus == null ? null : toStatus.name());
        history.setResponseCode(toStatus == null ? null : toStatus.name());
        history.setResponseMessage(responseMessage);
        history.setMetadata(metadata == null ? "{}" : metadata);
        paymentHistoryRepository.save(history);
    }
}
