package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.client.IdentityServiceClient;
import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.StepUpAuthenticationService;
import com.elvo.wallet.security.TransactionSigningChallengeService;
import com.elvo.wallet.security.WalletFraudVelocityService;
import com.elvo.wallet.service.EacReplayProtectionService;
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
                                        WalletFraudVelocityService fraudVelocityService) {
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
    }

    @Override
    @Transactional
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

        WalletFlowResult duplicate = idempotencyService.get(command.idempotencyKey()).orElse(null);
        if (duplicate != null) {
            return duplicate;
        }

        if (fraudVelocityService.isSuspicious(WalletFraudVelocityService.Operation.WITHDRAWAL, command.userId(), command.amount())) {
            return failed(command.walletId(), command.idempotencyKey(), "Velocity risk detected");
        }

        String sagaReference = resolveReference(command.reference(), command.idempotencyKey());
        sagaOrchestrator.begin("withdrawal", command.walletId(), command.amount(), sagaReference);

        if (!identityServiceClient.isUserActive(command.userId())) {
            return failed(command.walletId(), command.idempotencyKey(), "User is not active");
        }

        if (!identityServiceClient.verifyEsp(command.userId(), command.espCode())
                || !identityServiceClient.verifyEac(command.userId(), command.eacCode())) {
            return failed(command.walletId(), command.idempotencyKey(), "ESP/EAC verification failed");
        }

        boolean requiresStepUp = stepUpAuthenticationService.requiresStepUpForWithdrawal(command.amount(), command.mode());
        if (requiresStepUp && !transactionSigningChallengeService.isValidChallenge(
            command.transactionChallengeToken(),
            command.userId(),
            "WITHDRAWAL",
            command.amount(),
            command.targetNumber())) {
            return failed(command.walletId(), command.idempotencyKey(), "Transaction challenge confirmation required");
        }

        if (requiresStepUp && !stepUpAuthenticationService.isValidConfirmation(
                command.stepUpMethod(),
                command.stepUpToken(),
                replayBinding(command))) {
            return failed(command.walletId(), command.idempotencyKey(), "Step-up authentication required");
        }

        EacReplayProtectionService.EacValidationResult replayCheck = eacReplayProtectionService.validateAndConsume(
                command.userId(),
                command.eacCode(),
                replayBinding(command)
        );
        if (!replayCheck.accepted()) {
            return failed(command.walletId(), command.idempotencyKey(), replayCheck.message());
        }

        if (command.mode() != WithdrawalMode.REGISTERED_NUMBER
                && (command.targetNumber() == null || command.targetNumber().isBlank())) {
            return failed(command.walletId(), command.idempotencyKey(), "Target number is required for this withdrawal mode");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(command.walletId()).orElse(null);
        if (wallet == null) {
            return failed(command.walletId(), command.idempotencyKey(), "Wallet not found");
        }

        if (!limitEnforcementService.validate(wallet.getId(), WalletLimitEnforcementService.FlowType.WITHDRAWAL, command.amount())) {
            return failed(command.walletId(), command.idempotencyKey(), "Withdrawal limits exceeded");
        }

        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            return failed(command.walletId(), command.idempotencyKey(), "Wallet is frozen");
        }

        BigDecimal available = wallet.getBalance().subtract(wallet.getReservedBalance());
        if (available.compareTo(command.amount()) < 0) {
            return failed(command.walletId(), command.idempotencyKey(), "Insufficient balance");
        }

        BigDecimal updatedBalance = wallet.getBalance().subtract(command.amount());
        if (updatedBalance.signum() < 0) {
            return failed(command.walletId(), command.idempotencyKey(), "Negative balance prevention triggered");
        }

        wallet.setBalance(updatedBalance);

        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setType(Transaction.TransactionType.WITHDRAWAL);
        transaction.setAmount(command.amount());
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setReference(resolveReference(command.reference(), command.idempotencyKey()));
        transactionRepository.save(transaction);

        ledgerIntegrationService.recordDoubleEntry("withdrawal", wallet.getId(), command.amount(), transaction.getReference());

        emitWithdrawalEvent(true, wallet.getId(), transaction.getReference(), command.amount(), null);
        limitEnforcementService.record(wallet.getId(), WalletLimitEnforcementService.FlowType.WITHDRAWAL, command.amount());

        sagaOrchestrator.complete("withdrawal", wallet.getId(), command.amount(), transaction.getReference());

        WalletFlowResult result = WalletFlowResult.success(
                "Withdrawal completed",
                wallet.getId(),
                transaction.getId(),
                "wallet.withdrawal.completed");
        idempotencyService.put(command.idempotencyKey(), result);
        return result;
    }

    private WalletFlowResult failed(UUID walletId, String idempotencyKey, String message) {
        emitWithdrawalEvent(false, walletId, idempotencyKey, null, message);
        WalletFlowResult result = WalletFlowResult.failure(message, walletId, "wallet.withdrawal.failed");
        sagaOrchestrator.compensate("withdrawal", walletId, null, idempotencyKey, new IllegalStateException(message));
        idempotencyService.put(idempotencyKey, result);
        return result;
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
}
