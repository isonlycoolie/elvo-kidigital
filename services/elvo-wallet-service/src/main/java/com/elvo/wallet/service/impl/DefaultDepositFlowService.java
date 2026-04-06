package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.client.AgentServiceClient;
import com.elvo.wallet.client.IdentityServiceClient;
import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.MobileMoneyCallbackSecurityService;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.service.DepositFlowService;
import com.elvo.wallet.service.TransactionLifecycleService;
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
    private final MobileMoneyCallbackSecurityService mobileMoneyCallbackSecurityService;
    private final MobileCallbackReconciliationService callbackReconciliationService;
    private final WalletLimitEnforcementService limitEnforcementService;
    private final WalletSagaOrchestrator sagaOrchestrator;
    private final WalletEventPublisher eventPublisher;
    private final WalletFieldEncryptionService fieldEncryptionService;
    private final TransactionLifecycleService transactionLifecycleService;

    public DefaultDepositFlowService(WalletRepository walletRepository,
                                     TransactionRepository transactionRepository,
                                     IdentityServiceClient identityServiceClient,
                                     AgentServiceClient agentServiceClient,
                                     WalletIdempotencyService idempotencyService,
                                     WalletLedgerIntegrationService ledgerIntegrationService,
                                     MobileMoneyCallbackSecurityService mobileMoneyCallbackSecurityService,
                                     MobileCallbackReconciliationService callbackReconciliationService,
                                     WalletLimitEnforcementService limitEnforcementService,
                                     WalletSagaOrchestrator sagaOrchestrator,
                                     WalletEventPublisher eventPublisher,
                                     WalletFieldEncryptionService fieldEncryptionService,
                                     TransactionLifecycleService transactionLifecycleService) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.identityServiceClient = identityServiceClient;
        this.agentServiceClient = agentServiceClient;
        this.idempotencyService = idempotencyService;
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.mobileMoneyCallbackSecurityService = mobileMoneyCallbackSecurityService;
        this.callbackReconciliationService = callbackReconciliationService;
        this.sagaOrchestrator = sagaOrchestrator;
        this.eventPublisher = eventPublisher;
        this.limitEnforcementService = limitEnforcementService;
        this.fieldEncryptionService = fieldEncryptionService;
        this.transactionLifecycleService = transactionLifecycleService;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
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

        String endpointScope = "wallet.deposit.process";
        String userScope = scope(command.userId());
        String payloadFingerprint = WalletIdempotencyService.hashPayloadValue(String.join("|",
                String.valueOf(command.walletId()),
                String.valueOf(command.userId()),
                String.valueOf(command.amount()),
                String.valueOf(command.channel()),
                String.valueOf(command.reference()),
                String.valueOf(command.agentFloatAvailable()),
                String.valueOf(command.mobileCallbackReference())));

        WalletFlowResult duplicate = findDuplicate(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, command.walletId(), "wallet.deposit.failed");
        if (duplicate != null) {
            return duplicate;
        }

        String sagaReference = resolveReference(command.reference(), command.idempotencyKey());
        sagaOrchestrator.begin("deposit", command.walletId(), command.amount(), sagaReference);

        if (!identityServiceClient.isUserActive(command.userId())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "User is not active");
        }

        if (command.channel() == WalletChannel.AGENT
                && (!command.agentFloatAvailable() || !agentServiceClient.hasAvailableFloat(command.userId(), command.amount()))) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Agent float is insufficient");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(command.walletId()).orElse(null);
        if (wallet == null) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Wallet not found");
        }

        if (!limitEnforcementService.validate(wallet.getId(), WalletLimitEnforcementService.FlowType.DEPOSIT, command.amount())) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Deposit limits exceeded");
        }

        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            return failed(command.walletId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Wallet is frozen");
        }

        String resolvedReference = resolveReference(command.reference(), command.idempotencyKey());
        String encryptedReference = fieldEncryptionService.encrypt(resolvedReference);

        if (transactionRepository.existsByReference(encryptedReference)) {
            Transaction existing = transactionRepository.findFirstByReferenceOrderByCreatedAtDesc(encryptedReference);
            WalletFlowResult result = WalletFlowResult.success(
                    "Deposit already processed",
                    command.walletId(),
                    existing != null ? existing.getId() : null,
                    "wallet.deposit.completed");
                idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        boolean balanceApplied = false;
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(command.amount());
        transaction.setType(Transaction.TransactionType.DEPOSIT);
        transaction.setReference(encryptedReference);
        transaction.setExternalReference(resolvedReference);
        transaction = transactionLifecycleService.initialize(transaction, "Deposit initiated", correlationId(), resolvedReference);
        try {
            if (command.channel() == WalletChannel.MOBILE) {
            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.PENDING,
                "Awaiting telecom callback", correlationId(), null, null);
            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.AWAITING_CONFIRMATION,
                "Awaiting callback confirmation", correlationId(), null, null);

            if (command.mobileCallbackReference() == null || command.mobileCallbackReference().isBlank()) {
                return failed(wallet.getId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Mobile callback confirmation required");
            }

            if (!mobileMoneyCallbackSecurityService.isAuthenticatedCallback(
                    command.mobileCallbackReference(),
                    command.mobileCallbackSignature(),
                    command.mobileCallbackTimestamp(),
                    command.mobileCallbackSourceIp())) {
                transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.FAILED,
                    "Mobile callback authentication failed", correlationId(), "CALLBACK_AUTH_FAILED", "Untrusted callback payload");
                return failed(wallet.getId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Mobile callback authentication failed");
            }

            if (!callbackReconciliationService.consumeOnce(command.mobileCallbackReference(), command.mobileCallbackTimestamp())) {
                transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.FAILED,
                    "Mobile callback replay detected", correlationId(), "CALLBACK_REPLAY", "Duplicate or stale callback payload");
                return failed(wallet.getId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Mobile callback replay detected");
            }

            callbackReconciliationService.scheduleRetry(command.mobileCallbackReference(), wallet.getId(), command.amount());
            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.RETRYING,
                "Waiting for callback reconciliation", correlationId(), null, null);
            callbackReconciliationService.markReconciled(command.mobileCallbackReference());
            }

            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.PROCESSING,
                "Posting deposit", correlationId(), null, null);

            wallet.setBalance(wallet.getBalance().add(command.amount()));
            balanceApplied = true;
            ledgerIntegrationService.recordDoubleEntry("deposit", wallet.getId(), command.amount(), transaction.getReference());

            emitDepositEvent(true, wallet.getId(), transaction.getReference(), command.amount(), null);
            limitEnforcementService.record(wallet.getId(), WalletLimitEnforcementService.FlowType.DEPOSIT, command.amount());

            transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.COMPLETED,
                "Deposit completed", correlationId(), null, null);

            sagaOrchestrator.complete("deposit", wallet.getId(), command.amount(), transaction.getReference());

            WalletFlowResult result = WalletFlowResult.success(
                "Deposit completed",
                wallet.getId(),
                transaction.getId(),
                "wallet.deposit.completed");
            idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        } catch (RuntimeException ex) {
            if (balanceApplied) {
            wallet.setBalance(wallet.getBalance().subtract(command.amount()));
            transitionSafely(transaction, Transaction.TransactionStatus.REVERSED,
                "Deposit reversed after failure", "DEPOSIT_REVERSED", ex.getMessage());
            } else {
            transitionSafely(transaction, Transaction.TransactionStatus.FAILED,
                "Deposit failed before posting", "DEPOSIT_FAILED", ex.getMessage());
            }
            return failed(wallet.getId(), command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, "Deposit processing failed");
        }
    }

    private WalletFlowResult failed(UUID walletId,
                                    String idempotencyKey,
                                    String userScope,
                                    String endpointScope,
                                    String payloadFingerprint,
                                    String message) {
        emitDepositEvent(false, walletId, idempotencyKey, null, message);
        WalletFlowResult result = WalletFlowResult.failure(message, walletId, "wallet.deposit.failed");
        sagaOrchestrator.compensate("deposit", walletId, null, idempotencyKey, new IllegalStateException(message));
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

    private void emitDepositEvent(boolean success, UUID walletId, String reference, BigDecimal amount, String reason) {
        if (success) {
            AUDIT_LOG.info("event=wallet.deposit.completed walletId={} reference={} amount={}", walletId, reference, amount);
            eventPublisher.publish("wallet.deposit.completed", java.util.Map.of(
                    "walletId", walletId,
                    "reference", reference,
                    "amount", amount));
            return;
        }
        AUDIT_LOG.warn("event=wallet.deposit.failed walletId={} reference={} reason={}", walletId, reference, reason);
        eventPublisher.publish("wallet.deposit.failed", java.util.Map.of(
                "walletId", walletId,
                "reference", reference,
                "reason", reason == null ? "unknown" : reason));
    }

    private String resolveReference(String reference, String idempotencyKey) {
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return "dep-" + idempotencyKey + "-" + UUID.randomUUID();
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
