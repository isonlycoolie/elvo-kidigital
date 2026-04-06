package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.client.IdentityServiceClient;
import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.StepUpAuthenticationService;
import com.elvo.wallet.security.TransactionSigningChallengeService;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.security.WalletFraudVelocityService;
import com.elvo.wallet.service.EacReplayProtectionService;
import com.elvo.wallet.service.TransactionLifecycleService;
import com.elvo.wallet.service.WithdrawalFlowService;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;
import com.elvo.wallet.service.model.WithdrawalMode;
import com.elvo.wallet.service.orchestration.WalletSagaOrchestrator;

@Service
public class DefaultWithdrawalFlowService implements WithdrawalFlowService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.flow.withdrawal");

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IdentityServiceClient identityServiceClient;
    private final WalletIdempotencyService idempotencyService;
    private final WalletLedgerIntegrationService ledgerIntegrationService;
    private final WalletLimitEnforcementService limitEnforcementService;
    private final WalletSagaOrchestrator sagaOrchestrator;
    private final WalletEventPublisher eventPublisher;
    private final EacReplayProtectionService eacReplayProtectionService;
    private final StepUpAuthenticationService stepUpAuthenticationService;
    private final TransactionSigningChallengeService transactionSigningChallengeService;
    private final WalletFraudVelocityService fraudVelocityService;
    private final WalletFieldEncryptionService fieldEncryptionService;
    private final TransactionLifecycleService transactionLifecycleService;

    public DefaultWithdrawalFlowService(WalletRepository walletRepository,
                                        TransactionRepository transactionRepository,
                                        IdentityServiceClient identityServiceClient,
                                        WalletIdempotencyService idempotencyService,
                                        WalletLedgerIntegrationService ledgerIntegrationService,
                                        WalletLimitEnforcementService limitEnforcementService,
                                        WalletSagaOrchestrator sagaOrchestrator,
                                        WalletEventPublisher eventPublisher,
                                        EacReplayProtectionService eacReplayProtectionService,
                                        StepUpAuthenticationService stepUpAuthenticationService,
                                        TransactionSigningChallengeService transactionSigningChallengeService,
                                        WalletFraudVelocityService fraudVelocityService,
                                        WalletFieldEncryptionService fieldEncryptionService,
                                        TransactionLifecycleService transactionLifecycleService) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.identityServiceClient = identityServiceClient;
        this.idempotencyService = idempotencyService;
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.limitEnforcementService = limitEnforcementService;
        this.eventPublisher = eventPublisher;
        this.sagaOrchestrator = sagaOrchestrator;
        this.eacReplayProtectionService = eacReplayProtectionService;
        this.stepUpAuthenticationService = stepUpAuthenticationService;
        this.transactionSigningChallengeService = transactionSigningChallengeService;
        this.fraudVelocityService = fraudVelocityService;
        this.fieldEncryptionService = fieldEncryptionService;
        this.transactionLifecycleService = transactionLifecycleService;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletFlowResult process(WithdrawalCommand command) {
        if (command == null || command.walletId() == null || command.userId() == null || command.amount() == null) {
            return WalletFlowResult.failure("Invalid withdrawal request", null, "wallet.withdrawal.failed");
        }

        if (command.amount().signum() <= 0) {
            return WalletFlowResult.failure("Withdrawal amount must be greater than zero", command.walletId(), "wallet.withdrawal.failed");
        }

        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            return WalletFlowResult.failure("Idempotency key is required", command.walletId(), "wallet.withdrawal.failed");
        }

        String endpointScope = "wallet.withdrawal.process";
        String userScope = scope(command.userId());
        String payloadFingerprint = WalletIdempotencyService.hashPayloadValue(String.join("|",
                String.valueOf(command.walletId()),
                String.valueOf(command.userId()),
                String.valueOf(command.amount()),
                String.valueOf(command.mode()),
                String.valueOf(command.targetNumber()),
                String.valueOf(command.reference())));

        WalletFlowResult duplicate = findDuplicate(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, command.walletId(), "wallet.withdrawal.failed");
        if (duplicate != null) {
            return duplicate;
        }

        if (fraudVelocityService.isSuspicious(WalletFraudVelocityService.Operation.WITHDRAWAL, command.userId(), command.amount())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Velocity risk detected");
        }

        String sagaReference = resolveReference(command.reference(), command.idempotencyKey());
        sagaOrchestrator.begin("withdrawal", command.walletId(), command.amount(), sagaReference);

        if (!identityServiceClient.isUserActive(command.userId())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "User is not active");
        }

        if (!identityServiceClient.verifyEsp(command.userId(), command.espCode())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "ESP/EAC verification failed");
        }

        if (command.mode() != WithdrawalMode.OTHER_NUMBER
                && !identityServiceClient.verifyEac(command.userId(), command.eacCode())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "ESP/EAC verification failed");
        }

        boolean requiresStepUp = stepUpAuthenticationService.requiresStepUpForWithdrawal(command.amount(), command.mode());
        if (requiresStepUp && !transactionSigningChallengeService.isValidChallenge(
            command.transactionChallengeToken(),
            command.userId(),
            "WITHDRAWAL",
            command.amount(),
            command.targetNumber())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Transaction challenge confirmation required");
        }

        if (requiresStepUp && !stepUpAuthenticationService.isValidConfirmation(
                command.stepUpMethod(),
                command.stepUpToken(),
                replayBinding(command))) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Step-up authentication required");
        }

        if (command.mode() == WithdrawalMode.REGISTERED_NUMBER) {
            EacReplayProtectionService.EacValidationResult replayCheck = eacReplayProtectionService.validateAndConsume(
                    command.userId(),
                    command.eacCode(),
                    replayBinding(command)
            );
            if (!replayCheck.accepted()) {
                return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, replayCheck.message());
            }
        }

        if (command.mode() != WithdrawalMode.REGISTERED_NUMBER
                && (command.targetNumber() == null || command.targetNumber().isBlank())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Target number is required for this withdrawal mode");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(command.walletId()).orElse(null);
        if (wallet == null) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Wallet not found");
        }

        if (!limitEnforcementService.validate(wallet.getId(), WalletLimitEnforcementService.FlowType.WITHDRAWAL, command.amount())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Withdrawal limits exceeded");
        }

        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Wallet is frozen");
        }

        BigDecimal available = wallet.getBalance().subtract(wallet.getReservedBalance());
        if (available.compareTo(command.amount()) < 0) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Insufficient balance");
        }

        BigDecimal updatedBalance = wallet.getBalance().subtract(command.amount());
        if (updatedBalance.signum() < 0) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Negative balance prevention triggered");
        }

        wallet.setBalance(updatedBalance);
        if (command.mode() == WithdrawalMode.DEVICE_FREE) {
            wallet.setReservedBalance(wallet.getReservedBalance().add(command.amount()));
        }

        String resolvedReference = resolveReference(command.reference(), command.idempotencyKey());
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setType(Transaction.TransactionType.WITHDRAWAL);
        transaction.setAmount(command.amount());
        transaction.setReference(fieldEncryptionService.encrypt(resolvedReference));
        transaction.setExternalReference(resolvedReference);
        transaction = transactionLifecycleService.initialize(transaction, "Withdrawal initiated", correlationId(), resolvedReference);
        if (command.mode() == WithdrawalMode.REGISTERED_NUMBER || command.mode() == WithdrawalMode.OTHER_NUMBER) {
            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.PENDING,
            "Withdrawal queued", correlationId(), null, null);
        }
        if (command.mode() == WithdrawalMode.OTHER_NUMBER) {
            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.AWAITING_CONFIRMATION,
                "Waiting for EAC confirmation", correlationId(), null, null);

            if (!identityServiceClient.verifyEac(command.userId(), command.eacCode())) {
                return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "ESP/EAC verification failed");
            }

            EacReplayProtectionService.EacValidationResult replayCheck = eacReplayProtectionService.validateAndConsume(
                    command.userId(),
                    command.eacCode(),
                    replayBinding(command)
            );
            if (!replayCheck.accepted()) {
                if (isExpiredEac(replayCheck.message())) {
                    transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.EXPIRED,
                        "Withdrawal confirmation window expired", correlationId(), "WITHDRAWAL_EAC_EXPIRED", replayCheck.message());
                    return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Withdrawal confirmation expired");
                }
                return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, replayCheck.message());
            }
        }
        if (command.mode() == WithdrawalMode.DEVICE_FREE) {
            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.AWAITING_CONFIRMATION,
                "Waiting for device-free withdrawal confirmation", correlationId(), null, null);

            EacReplayProtectionService.EacValidationResult replayCheck = eacReplayProtectionService.validateAndConsume(
                    command.userId(),
                    command.eacCode(),
                    replayBinding(command)
            );
            if (!replayCheck.accepted()) {
                if (isExpiredEac(replayCheck.message())) {
                    transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.EXPIRED,
                        "Device-free withdrawal confirmation expired", correlationId(), "WITHDRAWAL_DEVICE_EXPIRED", replayCheck.message());
                    return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Device-free withdrawal expired");
                }
                return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, replayCheck.message());
            }

            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.RESERVED,
                "Funds reserved for device-free payout", correlationId(), null, null);
        }
        transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.PROCESSING,
            "Posting withdrawal", correlationId(), null, null);

        try {
            if (command.mode() == WithdrawalMode.DEVICE_FREE) {
                wallet.setReservedBalance(wallet.getReservedBalance().subtract(command.amount()));
            }
            ledgerIntegrationService.recordDoubleEntry("withdrawal", wallet.getId(), command.amount(), transaction.getReference());

            emitWithdrawalEvent(true, wallet.getId(), transaction.getReference(), command.amount(), null);
            limitEnforcementService.record(wallet.getId(), WalletLimitEnforcementService.FlowType.WITHDRAWAL, command.amount());

            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.COMPLETED,
                "Withdrawal completed", correlationId(), null, null);

            sagaOrchestrator.complete("withdrawal", wallet.getId(), command.amount(), transaction.getReference());

            WalletFlowResult result = WalletFlowResult.success(
                    "Withdrawal completed",
                    wallet.getId(),
                    transaction.getId(),
                    "wallet.withdrawal.completed");
            idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        } catch (RuntimeException ex) {
            if (command.mode() == WithdrawalMode.DEVICE_FREE) {
                restoreDeviceFreeFunds(wallet, command.amount());
                transitionSafely(transaction,
                    Transaction.TransactionStatus.REVERSED,
                    "Device-free withdrawal reversed after failure",
                    "WITHDRAWAL_REVERSED",
                    ex.getMessage());
                return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Device-free withdrawal reversed");
            }

            if (isTemporaryFailure(ex)) {
                transitionSafely(transaction,
                    Transaction.TransactionStatus.RETRYING,
                    "Temporary withdrawal posting failure; retry scheduled",
                    "WITHDRAWAL_RETRYING",
                    ex.getMessage());
            }
            transitionSafely(transaction,
                Transaction.TransactionStatus.FAILED,
                "Withdrawal processing failed",
                "WITHDRAWAL_PROCESSING_FAILED",
                ex.getMessage());
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Withdrawal processing failed");
        }
    }

    private WalletFlowResult failed(UUID walletId,
                                    String idempotencyKey,
                                    String userScope,
                                    String endpointScope,
                                    String payloadFingerprint,
                                    String message) {
        emitWithdrawalEvent(false, walletId, idempotencyKey, null, message);
        WalletFlowResult result = WalletFlowResult.failure(message, walletId, "wallet.withdrawal.failed");
        sagaOrchestrator.compensate("withdrawal", walletId, null, idempotencyKey, new IllegalStateException(message));
        idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
        return result;
    }

    private WalletFlowResult findDuplicate(String key,
                                           String userScope,
                                           String endpointScope,
                                           String payloadFingerprint,
                                           UUID walletId,
                                           String eventType) {
        try {
            return idempotencyService.get(key, userScope, endpointScope, payloadFingerprint).orElse(null);
        } catch (RuntimeException ex) {
            return WalletFlowResult.failure(ex.getMessage(), walletId, eventType);
        }
    }

    private String scope(UUID userId) {
        return userId == null ? "anonymous" : userId.toString();
    }

    private void emitWithdrawalEvent(boolean success, UUID walletId, String reference, BigDecimal amount, String reason) {
        if (success) {
            AUDIT_LOG.info("event=wallet.withdrawal.completed walletId={} reference={} amount={}", walletId, reference, amount);
            eventPublisher.publish("wallet.withdrawal.completed", java.util.Map.of(
                    "walletId", walletId,
                    "reference", reference,
                    "amount", amount));
            return;
        }
        AUDIT_LOG.warn("event=wallet.withdrawal.failed walletId={} reference={} reason={}", walletId, reference, reason);
        eventPublisher.publish("wallet.withdrawal.failed", java.util.Map.of(
                "walletId", walletId,
                "reference", reference,
                "reason", reason == null ? "unknown" : reason));
    }

    private String resolveReference(String reference, String idempotencyKey) {
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return "wd-" + idempotencyKey + "-" + UUID.randomUUID();
    }

    private String replayBinding(WithdrawalCommand command) {
        String target = command.targetNumber() == null ? "" : command.targetNumber().trim();
        String amount = command.amount() == null ? "0" : command.amount().stripTrailingZeros().toPlainString();
        return command.userId()
                + ":WITHDRAWAL"
                + ":" + command.mode()
                + ":" + amount
                + ":" + target;
    }

    private String correlationId() {
        return MDC.get("correlationId");
    }

    private boolean isExpiredEac(String message) {
        return message != null && message.toLowerCase().contains("expired");
    }

    private boolean isTemporaryFailure(RuntimeException exception) {
        if (exception == null || exception.getMessage() == null) {
            return false;
        }
        String message = exception.getMessage().toLowerCase();
        return message.contains("timeout")
                || message.contains("temporary")
                || message.contains("unavailable")
                || message.contains("retry");
    }

    private void transitionSafely(Transaction transaction,
                                  Transaction.TransactionStatus nextStatus,
                                  String reason,
                                  String failureCode,
                                  String failureMessage) {
        try {
            transactionLifecycleService.transition(transaction, nextStatus, reason, correlationId(), failureCode, failureMessage);
        } catch (RuntimeException ignored) {
            // Best effort transition in withdrawal failure path.
        }
    }

    private void restoreDeviceFreeFunds(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().add(amount));
        BigDecimal updatedReserved = wallet.getReservedBalance().subtract(amount);
        if (updatedReserved.signum() < 0) {
            updatedReserved = BigDecimal.ZERO;
        }
        wallet.setReservedBalance(updatedReserved);
    }
}
