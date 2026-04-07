package com.elvo.billing.service.orchestration;

import java.util.UUID;

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
public class PaymentFlow {

    private final UtilityPaymentValidator validator;
    private final ProviderResolver providerResolver;
    private final BillPaymentRepository billPaymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    public PaymentFlow(
            UtilityPaymentValidator validator,
            ProviderResolver providerResolver,
            BillPaymentRepository billPaymentRepository,
            PaymentHistoryRepository paymentHistoryRepository) {
        this.validator = validator;
        this.providerResolver = providerResolver;
        this.billPaymentRepository = billPaymentRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
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
        validator.validateForPayment(paymentRequest, billCategory);

        BillingAdapter adapter = providerResolver.resolve(serviceCode);
        PaymentResponseDto adapterResponse = adapter.pay(paymentRequest);

        BillPayment payment = new BillPayment();
        payment.setPaymentId(adapterResponse.getPaymentId() != null ? adapterResponse.getPaymentId() : UUID.randomUUID());
        payment.setRequestId(normalizeRequestValue(requestId));
        payment.setCorrelationId(normalizeRequestValue(correlationId));
        payment.setIdempotencyKey(normalizeRequestValue(idempotencyKey));
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

        if (adapterResponse.getPaymentId() == null) {
            adapterResponse.setPaymentId(payment.getPaymentId());
        }
        return adapterResponse;
    }

    private static String normalizeRequestValue(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }
}
