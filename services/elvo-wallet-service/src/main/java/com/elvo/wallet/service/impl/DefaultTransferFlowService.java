package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.StepUpAuthenticationService;
import com.elvo.wallet.security.TransactionSigningChallengeService;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.security.WalletFraudVelocityService;
import com.elvo.wallet.service.TransactionLifecycleService;
import com.elvo.wallet.service.TransferFlowService;
import com.elvo.wallet.service.model.TransferCommand;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.orchestration.WalletSagaOrchestrator;

@Service
public class DefaultTransferFlowService implements TransferFlowService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.flow.transfer");

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletIdempotencyService idempotencyService;
    private final WalletLedgerIntegrationService ledgerIntegrationService;
    private final WalletLimitEnforcementService limitEnforcementService;
    private final WalletSagaOrchestrator sagaOrchestrator;
    private final WalletEventPublisher eventPublisher;
    private final StepUpAuthenticationService stepUpAuthenticationService;
    private final TransactionSigningChallengeService transactionSigningChallengeService;
    private final WalletFraudVelocityService fraudVelocityService;
    private final WalletFieldEncryptionService fieldEncryptionService;
    private final TransactionLifecycleService transactionLifecycleService;

    public DefaultTransferFlowService(WalletRepository walletRepository,
                                      TransactionRepository transactionRepository,
                                      WalletIdempotencyService idempotencyService,
                                      WalletLedgerIntegrationService ledgerIntegrationService,
                                      WalletLimitEnforcementService limitEnforcementService,
                                      WalletSagaOrchestrator sagaOrchestrator,
                                      WalletEventPublisher eventPublisher,
                                      StepUpAuthenticationService stepUpAuthenticationService,
                                      TransactionSigningChallengeService transactionSigningChallengeService,
                                      WalletFraudVelocityService fraudVelocityService,
                                      WalletFieldEncryptionService fieldEncryptionService,
                                      TransactionLifecycleService transactionLifecycleService) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.limitEnforcementService = limitEnforcementService;
        this.sagaOrchestrator = sagaOrchestrator;
        this.eventPublisher = eventPublisher;
        this.stepUpAuthenticationService = stepUpAuthenticationService;
        this.transactionSigningChallengeService = transactionSigningChallengeService;
        this.fraudVelocityService = fraudVelocityService;
        this.fieldEncryptionService = fieldEncryptionService;
        this.transactionLifecycleService = transactionLifecycleService;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public WalletFlowResult process(TransferCommand command) {
        if (command == null || command.sourceWalletId() == null || command.targetWalletId() == null || command.amount() == null) {
            return WalletFlowResult.failure("Invalid transfer request", null, "wallet.transfer.failed");
        }

        if (command.amount().signum() <= 0) {
            return WalletFlowResult.failure("Transfer amount must be greater than zero", command.sourceWalletId(), "wallet.transfer.failed");
        }

        if (command.sourceWalletId().equals(command.targetWalletId())) {
            return WalletFlowResult.failure("Source and target wallets must be different", command.sourceWalletId(), "wallet.transfer.failed");
        }

        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            return WalletFlowResult.failure("Idempotency key is required", command.sourceWalletId(), "wallet.transfer.failed");
        }

        String endpointScope = "wallet.transfer.process";
        String userScope = scope(command.userId());
        String payloadFingerprint = WalletIdempotencyService.hashPayloadValue(String.join("|",
            String.valueOf(command.sourceWalletId()),
            String.valueOf(command.targetWalletId()),
            String.valueOf(command.userId()),
            String.valueOf(command.amount()),
            String.valueOf(command.reference())));

        boolean requiresStepUp = stepUpAuthenticationService.requiresStepUpForTransfer(command.amount());
        if (requiresStepUp && !transactionSigningChallengeService.isValidChallenge(
                command.transactionChallengeToken(),
                command.userId(),
                "TRANSFER",
                command.amount(),
                command.targetWalletId() == null ? null : command.targetWalletId().toString())) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Transaction challenge confirmation required");
        }

        if (requiresStepUp && !stepUpAuthenticationService.isValidConfirmation(
                command.stepUpMethod(),
                command.stepUpToken(),
                transferBinding(command))) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Step-up authentication required");
        }

        WalletFlowResult duplicate = findDuplicate(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, command.sourceWalletId(), "wallet.transfer.failed");
        if (duplicate != null) {
            return duplicate;
        }

        if (fraudVelocityService.isSuspicious(WalletFraudVelocityService.Operation.TRANSFER, command.userId(), command.amount())) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Velocity risk detected");
        }

        String sagaReference = resolveReference(command.reference(), command.idempotencyKey());
        sagaOrchestrator.begin("transfer", command.sourceWalletId(), command.amount(), sagaReference);

        Wallet source = walletRepository.findByIdForUpdate(command.sourceWalletId()).orElse(null);
        Wallet target = walletRepository.findByIdForUpdate(command.targetWalletId()).orElse(null);

        if (source != null && !limitEnforcementService.validate(source.getId(), WalletLimitEnforcementService.FlowType.TRANSFER, command.amount())) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Transfer limits exceeded");
        }

        if (source == null || target == null) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Source or target wallet not found");
        }

        if (source.getStatus() == Wallet.WalletStatus.FROZEN || target.getStatus() == Wallet.WalletStatus.FROZEN) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Source or target wallet is frozen");
        }

        String transferReference = resolveReference(command.reference(), command.idempotencyKey());
        String encryptedTransferReference = fieldEncryptionService.encrypt(transferReference);
        if (!transactionRepository.findByExternalReferenceAndStatusInForUpdate(
                transferReference,
                transactionLifecycleService.activeStatuses()).isEmpty()) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Transfer is already processing");
        }

        if (transactionRepository.existsByReference(encryptedTransferReference)) {
            Transaction existing = transactionRepository.findFirstByReferenceOrderByCreatedAtDesc(encryptedTransferReference);
            WalletFlowResult result = WalletFlowResult.success(
                    "Transfer already processed",
                    command.sourceWalletId(),
                    existing != null ? existing.getId() : null,
                    "wallet.transfer.completed");
                idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        BigDecimal available = source.getBalance().subtract(source.getReservedBalance());
        if (available.compareTo(command.amount()) < 0) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Insufficient balance for transfer");
        }

        source.setBalance(source.getBalance().subtract(command.amount()));
        target.setBalance(target.getBalance().add(command.amount()));

        Transaction debit = new Transaction();
        debit.setWallet(source);
        debit.setType(Transaction.TransactionType.TRANSFER);
        debit.setAmount(command.amount());
        debit.setReference(fieldEncryptionService.encrypt(transferReference + "-debit"));
        debit.setExternalReference(transferReference);
        debit = transactionLifecycleService.initialize(debit, "Transfer initiated", correlationId(), transferReference);

        Transaction credit = new Transaction();
        credit.setWallet(target);
        credit.setType(Transaction.TransactionType.TRANSFER);
        credit.setAmount(command.amount());
        credit.setReference(fieldEncryptionService.encrypt(transferReference + "-credit"));
        credit.setExternalReference(transferReference);
        credit = transactionLifecycleService.initialize(credit, "Transfer initiated", correlationId(), transferReference);

        try {
            transactionLifecycleService.transition(debit, Transaction.TransactionStatus.PROCESSING,
                "Posting transfer debit", correlationId(), null, null);
            transactionLifecycleService.transition(credit, Transaction.TransactionStatus.PROCESSING,
                "Posting transfer credit", correlationId(), null, null);

            ledgerIntegrationService.recordDoubleEntry("transfer", source.getId(), command.amount(), transferReference);
            ledgerIntegrationService.recordDoubleEntry("transfer", target.getId(), command.amount(), transferReference);

            AUDIT_LOG.info("event=wallet.transfer.completed sourceWalletId={} targetWalletId={} amount={} reference={}",
                source.getId(),
                target.getId(),
                command.amount(),
                transferReference);
            eventPublisher.publish("wallet.transfer.completed", java.util.Map.of(
                "sourceWalletId", source.getId(),
                "targetWalletId", target.getId(),
                "amount", command.amount(),
                "reference", transferReference));

            limitEnforcementService.record(source.getId(), WalletLimitEnforcementService.FlowType.TRANSFER, command.amount());

            transactionLifecycleService.transition(debit, Transaction.TransactionStatus.COMPLETED,
                "Transfer completed", correlationId(), null, null);
            transactionLifecycleService.transition(credit, Transaction.TransactionStatus.COMPLETED,
                "Transfer completed", correlationId(), null, null);

            sagaOrchestrator.complete("transfer", source.getId(), command.amount(), transferReference);

            WalletFlowResult result = WalletFlowResult.success(
                "Transfer completed",
                source.getId(),
                debit.getId(),
                "wallet.transfer.completed");
            idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        } catch (RuntimeException ex) {
            transitionSafely(debit, Transaction.TransactionStatus.REVERSED,
                "Transfer compensated after failure", "TRANSFER_REVERSED", ex.getMessage());
            transitionSafely(credit, Transaction.TransactionStatus.REVERSED,
                "Transfer compensated after failure", "TRANSFER_REVERSED", ex.getMessage());
            return failed(command.sourceWalletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Transfer processing failed");
        }
    }

    private WalletFlowResult failed(UUID walletId,
                                    String idempotencyKey,
                                    String userScope,
                                    String endpointScope,
                                    String payloadFingerprint,
                                    String message) {
        AUDIT_LOG.warn("event=wallet.transfer.failed sourceWalletId={} reason={}", walletId, message);
        eventPublisher.publish("wallet.transfer.failed", java.util.Map.of(
                "sourceWalletId", walletId,
                "reason", message));
        WalletFlowResult result = WalletFlowResult.failure(message, walletId, "wallet.transfer.failed");
        sagaOrchestrator.compensate("transfer", walletId, null, idempotencyKey, new IllegalStateException(message));
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

    private String resolveReference(String reference, String idempotencyKey) {
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return "trf-" + idempotencyKey + "-" + UUID.randomUUID();
    }

    private String transferBinding(TransferCommand command) {
        String amount = command.amount() == null ? "0" : command.amount().stripTrailingZeros().toPlainString();
        return command.sourceWalletId() + ":" + command.targetWalletId() + ":" + amount;
    }

    private String correlationId() {
        return MDC.get("correlationId");
    }

    private void transitionSafely(Transaction transaction,
                                  Transaction.TransactionStatus status,
                                  String reason,
                                  String failureCode,
                                  String failureMessage) {
        try {
            transactionLifecycleService.transition(transaction, status, reason, correlationId(), failureCode, failureMessage);
        } catch (RuntimeException ignored) {
            // Best effort lifecycle transition during failure handling.
        }
    }
}
