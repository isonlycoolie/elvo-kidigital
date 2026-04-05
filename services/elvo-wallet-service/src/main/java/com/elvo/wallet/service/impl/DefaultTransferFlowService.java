package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
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

    public DefaultTransferFlowService(WalletRepository walletRepository,
                                      TransactionRepository transactionRepository,
                                      WalletIdempotencyService idempotencyService,
                                      WalletLedgerIntegrationService ledgerIntegrationService,
                                      WalletLimitEnforcementService limitEnforcementService,
                                      WalletSagaOrchestrator sagaOrchestrator) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.limitEnforcementService = limitEnforcementService;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Override
    @Transactional
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

        WalletFlowResult duplicate = idempotencyService.get(command.idempotencyKey()).orElse(null);
        if (duplicate != null) {
            return duplicate;
        }

        String sagaReference = resolveReference(command.reference(), command.idempotencyKey());
        sagaOrchestrator.begin("transfer", command.sourceWalletId(), command.amount(), sagaReference);

        Wallet source = walletRepository.findByIdForUpdate(command.sourceWalletId()).orElse(null);
        Wallet target = walletRepository.findByIdForUpdate(command.targetWalletId()).orElse(null);

        if (source != null && !limitEnforcementService.validate(source.getId(), WalletLimitEnforcementService.FlowType.TRANSFER, command.amount())) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), "Transfer limits exceeded");
        }

        if (source == null || target == null) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), "Source or target wallet not found");
        }

        if (source.getStatus() == Wallet.WalletStatus.FROZEN || target.getStatus() == Wallet.WalletStatus.FROZEN) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), "Source or target wallet is frozen");
        }

        String transferReference = resolveReference(command.reference(), command.idempotencyKey());
        if (transactionRepository.existsByReference(transferReference)) {
            Transaction existing = transactionRepository.findFirstByReferenceOrderByCreatedAtDesc(transferReference);
            WalletFlowResult result = WalletFlowResult.success(
                    "Transfer already processed",
                    command.sourceWalletId(),
                    existing != null ? existing.getId() : null,
                    "wallet.transfer.completed");
            idempotencyService.put(command.idempotencyKey(), result);
            return result;
        }

        BigDecimal available = source.getBalance().subtract(source.getReservedBalance());
        if (available.compareTo(command.amount()) < 0) {
            return failed(command.sourceWalletId(), command.idempotencyKey(), "Insufficient balance for transfer");
        }

        source.setBalance(source.getBalance().subtract(command.amount()));
        target.setBalance(target.getBalance().add(command.amount()));

        Transaction debit = new Transaction();
        debit.setWallet(source);
        debit.setType(Transaction.TransactionType.TRANSFER);
        debit.setAmount(command.amount());
        debit.setStatus(Transaction.TransactionStatus.COMPLETED);
        debit.setReference(transferReference + "-debit");
        transactionRepository.save(debit);

        Transaction credit = new Transaction();
        credit.setWallet(target);
        credit.setType(Transaction.TransactionType.TRANSFER);
        credit.setAmount(command.amount());
        credit.setStatus(Transaction.TransactionStatus.COMPLETED);
        credit.setReference(transferReference + "-credit");
        transactionRepository.save(credit);

        ledgerIntegrationService.recordDoubleEntry("transfer", source.getId(), command.amount(), transferReference);
        ledgerIntegrationService.recordDoubleEntry("transfer", target.getId(), command.amount(), transferReference);

        AUDIT_LOG.info("event=wallet.transfer.completed sourceWalletId={} targetWalletId={} amount={} reference={}",
                source.getId(),
                target.getId(),
                command.amount(),
                transferReference);

        limitEnforcementService.record(source.getId(), WalletLimitEnforcementService.FlowType.TRANSFER, command.amount());

        sagaOrchestrator.complete("transfer", source.getId(), command.amount(), transferReference);

        WalletFlowResult result = WalletFlowResult.success(
                "Transfer completed",
                source.getId(),
                debit.getId(),
                "wallet.transfer.completed");
        idempotencyService.put(command.idempotencyKey(), result);
        return result;
    }

    private WalletFlowResult failed(UUID walletId, String idempotencyKey, String message) {
        AUDIT_LOG.warn("event=wallet.transfer.failed sourceWalletId={} reason={}", walletId, message);
        WalletFlowResult result = WalletFlowResult.failure(message, walletId, "wallet.transfer.failed");
        sagaOrchestrator.compensate("transfer", walletId, null, idempotencyKey, new IllegalStateException(message));
        idempotencyService.put(idempotencyKey, result);
        return result;
    }

    private String resolveReference(String reference, String idempotencyKey) {
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return "trf-" + idempotencyKey + "-" + UUID.randomUUID();
    }
}
