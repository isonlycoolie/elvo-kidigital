package com.elvo.billing.service.orchestration;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.service.BillingTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class BillingTransactionOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(BillingTransactionOrchestrator.class);

    private final BillPaymentRepository billPaymentRepository;
    private final BillingTransactionService billingTransactionService;

    public BillingTransactionOrchestrator(
            BillPaymentRepository billPaymentRepository,
            BillingTransactionService billingTransactionService) {
        this.billPaymentRepository = billPaymentRepository;
        this.billingTransactionService = billingTransactionService;
    }

    @RabbitListener(queues = "${elvo.messaging.wallet.completed-queue:wallet.transaction.completed.queue}")
    public void onWalletCompleted(Map<String, Object> event) {
        UUID paymentId = resolvePaymentId(event);
        if (paymentId == null) {
            LOG.warn("billing_orchestrator_skip_complete reason=missing_payment_id");
            return;
        }

        Optional<BillPayment> paymentOptional = billPaymentRepository.getPaymentById(paymentId);
        if (paymentOptional.isEmpty()) {
            LOG.warn("billing_orchestrator_skip_complete reason=payment_not_found paymentId={}", paymentId);
            return;
        }

        BillPayment payment = paymentOptional.get();
        billingTransactionService.completeTransaction(payment);
        billPaymentRepository.updatePaymentStatus(paymentId, PaymentStatus.SUCCESS);
        LOG.info("billing_orchestrator_complete paymentId={}", paymentId);
    }

    @RabbitListener(queues = "${elvo.messaging.wallet.failed-queue:wallet.transaction.failed.queue}")
    public void onWalletFailed(Map<String, Object> event) {
        UUID paymentId = resolvePaymentId(event);
        if (paymentId == null) {
            LOG.warn("billing_orchestrator_skip_reverse reason=missing_payment_id");
            return;
        }

        Optional<BillPayment> paymentOptional = billPaymentRepository.getPaymentById(paymentId);
        if (paymentOptional.isEmpty()) {
            LOG.warn("billing_orchestrator_skip_reverse reason=payment_not_found paymentId={}", paymentId);
            return;
        }

        BillPayment payment = paymentOptional.get();
        String reason = payloadValue(event, "reason");
        billingTransactionService.reverseTransaction(payment, reason == null ? "wallet failure event" : reason);
        LOG.info("billing_orchestrator_reverse paymentId={}", paymentId);
    }

    @SuppressWarnings("unchecked")
    private UUID resolvePaymentId(Map<String, Object> event) {
        String paymentIdValue = payloadValue(event, "paymentId");
        if (paymentIdValue == null) {
            paymentIdValue = payloadValue(event, "transactionId");
        }
        if (paymentIdValue == null) {
            return null;
        }
        try {
            return UUID.fromString(paymentIdValue);
        } catch (IllegalArgumentException ex) {
            LOG.warn("billing_orchestrator_skip_event reason=invalid_payment_id paymentId={}", paymentIdValue);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String payloadValue(Map<String, Object> event, String key) {
        if (event == null) {
            return null;
        }
        Object payload = event.get("payload");
        if (!(payload instanceof Map<?, ?> mapPayload)) {
            return null;
        }
        Object value = ((Map<String, Object>) mapPayload).get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
