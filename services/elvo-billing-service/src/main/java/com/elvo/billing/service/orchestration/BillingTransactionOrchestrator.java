package com.elvo.billing.service.orchestration;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.messaging.DeadLetterPublishingService;
import com.elvo.billing.monitoring.SecurityMonitoringService;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.security.BillingInternalEventInputValidator;
import com.elvo.billing.security.BillingOperationRateLimitService;
import com.elvo.billing.security.BillingPaymentStateTransitionValidator;
import com.elvo.billing.security.BillingServiceAuthorizationMatrix;
import com.elvo.billing.security.InternalServiceMessageAuthenticator;
import com.elvo.billing.service.BillingTransactionService;
import com.elvo.billing.service.impl.InternalEventIdempotencyService;

@Component
public class BillingTransactionOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(BillingTransactionOrchestrator.class);
    private static final String EXPECTED_SOURCE_SERVICE = "elvo-wallet-service";
    private static final String COMPLETED_QUEUE = "wallet.transaction.completed.queue";
    private static final String FAILED_QUEUE = "wallet.transaction.failed.queue";

    private final BillPaymentRepository billPaymentRepository;
    private final BillingTransactionService billingTransactionService;
    private final BillingServiceAuthorizationMatrix authorizationMatrix;
    private final InternalEventIdempotencyService internalEventIdempotencyService;
    private final BillingInternalEventInputValidator inputValidator;
    private final BillingPaymentStateTransitionValidator stateTransitionValidator;
    private final SecurityMonitoringService securityMonitoringService;
    private final BillingOperationRateLimitService operationRateLimitService;
    private final DeadLetterPublishingService deadLetterPublishingService;

    public BillingTransactionOrchestrator(
            BillPaymentRepository billPaymentRepository,
            BillingTransactionService billingTransactionService,
            BillingServiceAuthorizationMatrix authorizationMatrix,
            InternalEventIdempotencyService internalEventIdempotencyService,
            BillingInternalEventInputValidator inputValidator,
            BillingPaymentStateTransitionValidator stateTransitionValidator) {
        this(
                billPaymentRepository,
                billingTransactionService,
                authorizationMatrix,
                internalEventIdempotencyService,
                inputValidator,
                stateTransitionValidator,
                null,
                null,
                null);
    }

    public BillingTransactionOrchestrator(
            BillPaymentRepository billPaymentRepository,
            BillingTransactionService billingTransactionService,
            BillingServiceAuthorizationMatrix authorizationMatrix,
            InternalEventIdempotencyService internalEventIdempotencyService,
            BillingInternalEventInputValidator inputValidator,
            BillingPaymentStateTransitionValidator stateTransitionValidator,
            @Nullable SecurityMonitoringService securityMonitoringService) {
        this(
                billPaymentRepository,
                billingTransactionService,
                authorizationMatrix,
                internalEventIdempotencyService,
                inputValidator,
                stateTransitionValidator,
                securityMonitoringService,
                null,
                null);
    }

    public BillingTransactionOrchestrator(
            BillPaymentRepository billPaymentRepository,
            BillingTransactionService billingTransactionService,
            BillingServiceAuthorizationMatrix authorizationMatrix,
            InternalEventIdempotencyService internalEventIdempotencyService,
            BillingInternalEventInputValidator inputValidator,
            BillingPaymentStateTransitionValidator stateTransitionValidator,
            @Nullable SecurityMonitoringService securityMonitoringService,
            @Nullable BillingOperationRateLimitService operationRateLimitService,
            @Nullable DeadLetterPublishingService deadLetterPublishingService) {
        this.billPaymentRepository = billPaymentRepository;
        this.billingTransactionService = billingTransactionService;
        this.authorizationMatrix = authorizationMatrix;
        this.internalEventIdempotencyService = internalEventIdempotencyService;
        this.inputValidator = inputValidator;
        this.stateTransitionValidator = stateTransitionValidator;
        this.securityMonitoringService = securityMonitoringService;
        this.operationRateLimitService = operationRateLimitService;
        this.deadLetterPublishingService = deadLetterPublishingService;
    }

    @RabbitListener(queues = "${elvo.messaging.wallet.completed-queue:wallet.transaction.completed.queue}")
    @Transactional
    public void onWalletCompleted(Map<String, Object> event) {
        if (!authorizationMatrix.isAllowed("wallet-service", "CONSUME", COMPLETED_QUEUE)) {
            LOG.warn("billing_orchestrator_skip_complete reason=queue_not_authorized");
            monitorSuspicious("billing.security.unauthorized_queue_access", "queue_not_authorized", COMPLETED_QUEUE, null);
            deadLetter(COMPLETED_QUEUE, "queue_not_authorized", event);
            return;
        }

        if (!InternalServiceMessageAuthenticator.isTrusted(event, EXPECTED_SOURCE_SERVICE)) {
            LOG.warn("billing_orchestrator_skip_complete reason=invalid_service_token");
            monitorInvalidSignature("onWalletCompleted");
            deadLetter(COMPLETED_QUEUE, "invalid_service_token", event);
            return;
        }
        if (!InternalServiceMessageAuthenticator.isReplaySafe(event)) {
            LOG.warn("billing_orchestrator_skip_complete reason=replay_validation_failed");
            monitorReplay("onWalletCompleted");
            deadLetter(COMPLETED_QUEUE, "replay_validation_failed", event);
            return;
        }
        if (!inputValidator.isValidWalletCompletedEvent(event)) {
            LOG.warn("billing_orchestrator_skip_complete reason=invalid_event_payload");
            monitorSuspicious("billing.security.invalid_event_payload", "invalid_event_payload", COMPLETED_QUEUE, rootValue(event, "eventType"));
            deadLetter(COMPLETED_QUEUE, "invalid_event_payload", event);
            return;
        }
        if (!enforceEventRateLimit(event, "wallet.transaction.completed")) {
            LOG.warn("billing_orchestrator_skip_complete reason=rate_limited");
            deadLetter(COMPLETED_QUEUE, "rate_limited", event);
            return;
        }

        UUID paymentId = resolvePaymentId(event);
        if (paymentId == null) {
            LOG.warn("billing_orchestrator_skip_complete reason=missing_payment_id");
            deadLetter(COMPLETED_QUEUE, "missing_payment_id", event);
            return;
        }
        String eventId = rootValue(event, InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD);
        String eventType = rootValue(event, "eventType");
        String idempotencyKey = payloadValue(event, "idempotencyKey");
        if (idempotencyKey == null) {
            idempotencyKey = paymentId.toString();
        }
        if (!internalEventIdempotencyService.markIfFirstProcessed(
                eventId,
                idempotencyKey,
                eventType == null ? "wallet.transaction.completed" : eventType,
                EXPECTED_SOURCE_SERVICE)) {
            LOG.warn("billing_orchestrator_skip_complete reason=duplicate_event eventId={}", eventId);
            deadLetter(COMPLETED_QUEUE, "duplicate_event", event);
            return;
        }

        Optional<BillPayment> paymentOptional = billPaymentRepository.getPaymentByIdWithLock(paymentId);
        if (paymentOptional.isEmpty()) {
            LOG.warn("billing_orchestrator_skip_complete reason=payment_not_found paymentId={}", paymentId);
            deadLetter(COMPLETED_QUEUE, "payment_not_found", event);
            return;
        }

        BillPayment payment = paymentOptional.get();
        if (!stateTransitionValidator.canTransition(payment.getStatus(), PaymentStatus.SUCCESS)) {
            LOG.warn("billing_orchestrator_skip_complete reason=invalid_state_transition paymentId={} status={}",
                    paymentId,
                    payment.getStatus());
            monitorSuspicious("billing.security.invalid_state_transition", "invalid_state_transition", COMPLETED_QUEUE, paymentId.toString());
            deadLetter(COMPLETED_QUEUE, "invalid_state_transition", event);
            return;
        }
        billingTransactionService.completeTransaction(payment);
        billPaymentRepository.updatePaymentStatus(paymentId, PaymentStatus.SUCCESS);
        LOG.info("billing_orchestrator_complete paymentId={}", paymentId);
    }

    @RabbitListener(queues = "${elvo.messaging.wallet.failed-queue:wallet.transaction.failed.queue}")
    @Transactional
    public void onWalletFailed(Map<String, Object> event) {
        if (!authorizationMatrix.isAllowed("wallet-service", "CONSUME", FAILED_QUEUE)) {
            LOG.warn("billing_orchestrator_skip_reverse reason=queue_not_authorized");
            monitorSuspicious("billing.security.unauthorized_queue_access", "queue_not_authorized", FAILED_QUEUE, null);
            deadLetter(FAILED_QUEUE, "queue_not_authorized", event);
            return;
        }

        if (!InternalServiceMessageAuthenticator.isTrusted(event, EXPECTED_SOURCE_SERVICE)) {
            LOG.warn("billing_orchestrator_skip_reverse reason=invalid_service_token");
            monitorInvalidSignature("onWalletFailed");
            deadLetter(FAILED_QUEUE, "invalid_service_token", event);
            return;
        }
        if (!InternalServiceMessageAuthenticator.isReplaySafe(event)) {
            LOG.warn("billing_orchestrator_skip_reverse reason=replay_validation_failed");
            monitorReplay("onWalletFailed");
            deadLetter(FAILED_QUEUE, "replay_validation_failed", event);
            return;
        }
        if (!inputValidator.isValidWalletFailedEvent(event)) {
            LOG.warn("billing_orchestrator_skip_reverse reason=invalid_event_payload");
            monitorSuspicious("billing.security.invalid_event_payload", "invalid_event_payload", FAILED_QUEUE, rootValue(event, "eventType"));
            deadLetter(FAILED_QUEUE, "invalid_event_payload", event);
            return;
        }
        if (!enforceEventRateLimit(event, "wallet.transaction.failed")) {
            LOG.warn("billing_orchestrator_skip_reverse reason=rate_limited");
            deadLetter(FAILED_QUEUE, "rate_limited", event);
            return;
        }

        UUID paymentId = resolvePaymentId(event);
        if (paymentId == null) {
            LOG.warn("billing_orchestrator_skip_reverse reason=missing_payment_id");
            deadLetter(FAILED_QUEUE, "missing_payment_id", event);
            return;
        }
        String eventId = rootValue(event, InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD);
        String eventType = rootValue(event, "eventType");
        String idempotencyKey = payloadValue(event, "idempotencyKey");
        if (idempotencyKey == null) {
            idempotencyKey = paymentId.toString();
        }
        if (!internalEventIdempotencyService.markIfFirstProcessed(
                eventId,
                idempotencyKey,
                eventType == null ? "wallet.transaction.failed" : eventType,
                EXPECTED_SOURCE_SERVICE)) {
            LOG.warn("billing_orchestrator_skip_reverse reason=duplicate_event eventId={}", eventId);
            deadLetter(FAILED_QUEUE, "duplicate_event", event);
            return;
        }

        Optional<BillPayment> paymentOptional = billPaymentRepository.getPaymentByIdWithLock(paymentId);
        if (paymentOptional.isEmpty()) {
            LOG.warn("billing_orchestrator_skip_reverse reason=payment_not_found paymentId={}", paymentId);
            deadLetter(FAILED_QUEUE, "payment_not_found", event);
            return;
        }

        BillPayment payment = paymentOptional.get();
        if (!stateTransitionValidator.canTransition(payment.getStatus(), PaymentStatus.REVERSED)) {
            LOG.warn("billing_orchestrator_skip_reverse reason=invalid_state_transition paymentId={} status={}",
                    paymentId,
                    payment.getStatus());
            monitorSuspicious("billing.security.invalid_state_transition", "invalid_state_transition", FAILED_QUEUE, paymentId.toString());
            deadLetter(FAILED_QUEUE, "invalid_state_transition", event);
            return;
        }
        String reason = payloadValue(event, "reason");
        billingTransactionService.reverseTransaction(payment, reason == null ? "wallet failure event" : reason);
        LOG.info("billing_orchestrator_reverse paymentId={}", paymentId);
    }

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

    private String rootValue(Map<String, Object> event, String key) {
        if (event == null || key == null || key.isBlank()) {
            return null;
        }
        Object value = event.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void monitorInvalidSignature(String operation) {
        if (securityMonitoringService != null) {
            securityMonitoringService.recordInvalidSignature(EXPECTED_SOURCE_SERVICE, operation);
        }
    }

    private void monitorReplay(String operation) {
        if (securityMonitoringService != null) {
            securityMonitoringService.recordReplayAttempt(EXPECTED_SOURCE_SERVICE, operation);
        }
    }

    private void monitorSuspicious(String eventType, String reason, String queue, String eventRef) {
        if (securityMonitoringService != null) {
            securityMonitoringService.recordSuspiciousEvent(eventType, reason, Map.of(
                    "queue", queue == null ? "unknown" : queue,
                    "eventRef", eventRef == null ? "unknown" : eventRef));
        }
    }

    private boolean enforceEventRateLimit(Map<String, Object> event, String fallbackType) {
        if (operationRateLimitService == null) {
            return true;
        }
        BillingOperationRateLimitService.RateLimitResult result = operationRateLimitService.enforce(
                BillingOperationRateLimitService.Operation.WALLET_EVENT_CONSUME,
                rootValue(event, "eventType") == null ? fallbackType : rootValue(event, "eventType"),
                rootValue(event, InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD));
        if (!result.allowed()) {
            monitorSuspicious("billing.security.event_rate_limit_exceeded", "rate_limit_exceeded", fallbackType, rootValue(event, InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD));
            return false;
        }
        return true;
    }

    private void deadLetter(String sourceQueue, String reason, Map<String, Object> event) {
        if (deadLetterPublishingService != null) {
            deadLetterPublishingService.publish(sourceQueue, reason, event);
        }
    }
}
