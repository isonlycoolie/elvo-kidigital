package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.client.AgentServiceClient;
import com.elvo.wallet.client.IdentityServiceClient;
import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.service.DepositFlowService;
import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.WalletChannel;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.orchestration.WalletSagaOrchestrator;

@Service
public class DefaultDepositFlowService implements DepositFlowService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.flow.deposit");

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IdentityServiceClient identityServiceClient;
    private final AgentServiceClient agentServiceClient;
    private final WalletIdempotencyService idempotencyService;
    private final WalletLedgerIntegrationService ledgerIntegrationService;
    private final MobileCallbackReconciliationService callbackReconciliationService;
    private final WalletLimitEnforcementService limitEnforcementService;
    private final WalletSagaOrchestrator sagaOrchestrator;

    public DefaultDepositFlowService(WalletRepository walletRepository,
                                     TransactionRepository transactionRepository,
                                     IdentityServiceClient identityServiceClient,
                                     AgentServiceClient agentServiceClient,
                                     WalletIdempotencyService idempotencyService,
                                     WalletLedgerIntegrationService ledgerIntegrationService,
                                     MobileCallbackReconciliationService callbackReconciliationService,
                                     WalletLimitEnforcementService limitEnforcementService,
                                     WalletSagaOrchestrator sagaOrchestrator) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.identityServiceClient = identityServiceClient;
        this.agentServiceClient = agentServiceClient;
        this.idempotencyService = idempotencyService;
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.callbackReconciliationService = callbackReconciliationService;
        this.sagaOrchestrator = sagaOrchestrator;
        this.limitEnforcementService = limitEnforcementService;
    }

    @Override
    @Transactional
    public WalletFlowResult process(DepositCommand command) {
        if (command == null || command.walletId() == null || command.userId() == null || command.amount() == null) {
            return WalletFlowResult.failure("Invalid deposit request", null, "wallet.deposit.failed");
        }

        if (command.amount().signum() <= 0) {
            return WalletFlowResult.failure("Deposit amount must be greater than zero", command.walletId(), "wallet.deposit.failed");
        }

        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            return WalletFlowResult.failure("Idempotency key is required", command.walletId(), "wallet.deposit.failed");
        }

        WalletFlowResult duplicate = idempotencyService.get(command.idempotencyKey()).orElse(null);
        if (duplicate != null) {
            return duplicate;
        }

        String sagaReference = resolveReference(command.reference(), command.idempotencyKey());
        sagaOrchestrator.begin("deposit", command.walletId(), command.amount(), sagaReference);

        if (!identityServiceClient.isUserActive(command.userId())) {
            return failed(command.walletId(), command.idempotencyKey(), "User is not active");
        }

        if (command.channel() == WalletChannel.AGENT
                && (!command.agentFloatAvailable() || !agentServiceClient.hasAvailableFloat(command.userId(), command.amount()))) {
            return failed(command.walletId(), command.idempotencyKey(), "Agent float is insufficient");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(command.walletId()).orElse(null);
        if (wallet == null) {
            return failed(command.walletId(), command.idempotencyKey(), "Wallet not found");
        }

        if (!limitEnforcementService.validate(wallet.getId(), WalletLimitEnforcementService.FlowType.DEPOSIT, command.amount())) {
            return failed(command.walletId(), command.idempotencyKey(), "Deposit limits exceeded");
        }

        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            return failed(command.walletId(), command.idempotencyKey(), "Wallet is frozen");
        }

        if (command.reference() != null && !command.reference().isBlank() && transactionRepository.existsByReference(command.reference())) {
            Transaction existing = transactionRepository.findFirstByReferenceOrderByCreatedAtDesc(command.reference());
            WalletFlowResult result = WalletFlowResult.success(
                    "Deposit already processed",
                    command.walletId(),
                    existing != null ? existing.getId() : null,
                    "wallet.deposit.completed");
            idempotencyService.put(command.idempotencyKey(), result);
            return result;
        }

        BigDecimal updatedBalance = wallet.getBalance().add(command.amount());
        wallet.setBalance(updatedBalance);

        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(command.amount());
        transaction.setType(Transaction.TransactionType.DEPOSIT);
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setReference(resolveReference(command.reference(), command.idempotencyKey()));
        transactionRepository.save(transaction);

        ledgerIntegrationService.recordDoubleEntry("deposit", wallet.getId(), command.amount(), transaction.getReference());

        if (command.channel() == WalletChannel.MOBILE && command.mobileCallbackReference() != null && !command.mobileCallbackReference().isBlank()) {
            callbackReconciliationService.scheduleRetry(command.mobileCallbackReference(), wallet.getId(), command.amount());
            callbackReconciliationService.markReconciled(command.mobileCallbackReference());
        }

        emitDepositEvent(true, wallet.getId(), transaction.getReference(), command.amount(), null);
        limitEnforcementService.record(wallet.getId(), WalletLimitEnforcementService.FlowType.DEPOSIT, command.amount());

        sagaOrchestrator.complete("deposit", wallet.getId(), command.amount(), transaction.getReference());

        WalletFlowResult result = WalletFlowResult.success(
                "Deposit completed",
                wallet.getId(),
                transaction.getId(),
                "wallet.deposit.completed");
        idempotencyService.put(command.idempotencyKey(), result);
        return result;
    }

    private WalletFlowResult failed(UUID walletId, String idempotencyKey, String message) {
        emitDepositEvent(false, walletId, idempotencyKey, null, message);
        WalletFlowResult result = WalletFlowResult.failure(message, walletId, "wallet.deposit.failed");
        sagaOrchestrator.compensate("deposit", walletId, null, idempotencyKey, new IllegalStateException(message));
        idempotencyService.put(idempotencyKey, result);
        return result;
    }

    private void emitDepositEvent(boolean success, UUID walletId, String reference, BigDecimal amount, String reason) {
        if (success) {
            AUDIT_LOG.info("event=wallet.deposit.completed walletId={} reference={} amount={}", walletId, reference, amount);
            return;
        }
        AUDIT_LOG.warn("event=wallet.deposit.failed walletId={} reference={} reason={}", walletId, reference, reason);
    }

    private String resolveReference(String reference, String idempotencyKey) {
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return "dep-" + idempotencyKey + "-" + UUID.randomUUID();
    }
}
