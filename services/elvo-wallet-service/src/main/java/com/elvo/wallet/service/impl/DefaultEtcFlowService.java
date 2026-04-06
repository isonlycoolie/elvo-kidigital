package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.EtcRepository;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.EtcBruteForceProtectionService;
import com.elvo.wallet.security.EtcCodePolicyService;
import com.elvo.wallet.security.EtcCodeSecurityService;
import com.elvo.wallet.service.EtcFlowService;
import com.elvo.wallet.service.TransactionLifecycleService;
import com.elvo.wallet.service.model.EtcCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultEtcFlowService implements EtcFlowService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.flow.etc");

    private final EtcRepository etcRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletIdempotencyService idempotencyService;
    private final WalletLedgerIntegrationService ledgerIntegrationService;
    private final WalletLimitEnforcementService limitEnforcementService;
    private final WalletEventPublisher eventPublisher;
    private final EtcCodeSecurityService etcCodeSecurityService;
    private final EtcCodePolicyService etcCodePolicyService;
    private final EtcBruteForceProtectionService etcBruteForceProtectionService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final int maxFailedAttemptsPerCode;

    public DefaultEtcFlowService(EtcRepository etcRepository,
                                 WalletRepository walletRepository,
                                 TransactionRepository transactionRepository,
                                 WalletIdempotencyService idempotencyService,
                                 WalletLedgerIntegrationService ledgerIntegrationService,
                                 WalletLimitEnforcementService limitEnforcementService,
                                 WalletEventPublisher eventPublisher,
                                 EtcCodeSecurityService etcCodeSecurityService,
                                 EtcCodePolicyService etcCodePolicyService,
                                 EtcBruteForceProtectionService etcBruteForceProtectionService,
                                 TransactionLifecycleService transactionLifecycleService,
                                 @org.springframework.beans.factory.annotation.Value("${elvo.security.etc.bruteforce.max-attempts-per-code:5}") int maxFailedAttemptsPerCode) {
        this.etcRepository = etcRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.eventPublisher = eventPublisher;
        this.limitEnforcementService = limitEnforcementService;
        this.etcCodeSecurityService = etcCodeSecurityService;
        this.etcCodePolicyService = etcCodePolicyService;
        this.etcBruteForceProtectionService = etcBruteForceProtectionService;
        this.transactionLifecycleService = transactionLifecycleService;
        this.maxFailedAttemptsPerCode = maxFailedAttemptsPerCode;
    }

    @Override
    @Transactional
    public WalletFlowResult generate(EtcCommand command) {
        if (command == null || command.walletId() == null || command.code() == null || command.expiresAt() == null) {
            return WalletFlowResult.failure("Invalid ETC generation request", null, "wallet.etc.failed");
        }

        if (!etcCodePolicyService.hasRequiredEntropy(command.code())) {
            return WalletFlowResult.failure("ETC code does not meet entropy requirements", command.walletId(), "wallet.etc.failed");
        }

        if (!etcCodePolicyService.isExpiryWithinWindow(command.expiresAt(), Instant.now())) {
            return WalletFlowResult.failure("ETC expiration window is invalid", command.walletId(), "wallet.etc.failed");
        }

        String endpointScope = "wallet.etc.generate";
        String userScope = scope(command.userId());
        String payloadFingerprint = WalletIdempotencyService.hashPayloadValue(String.join("|",
                String.valueOf(command.walletId()),
                String.valueOf(command.userId()),
                String.valueOf(command.code()),
                String.valueOf(command.expiresAt())));

        WalletFlowResult duplicate = findDuplicate(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, command.walletId());
        if (duplicate != null) {
            return duplicate;
        }

        Wallet wallet = walletRepository.findByIdForUpdate(command.walletId()).orElse(null);
        if (wallet == null) {
            WalletFlowResult result = WalletFlowResult.failure("Wallet not found", command.walletId(), "wallet.etc.failed");
            idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            WalletFlowResult result = WalletFlowResult.failure("Wallet is frozen", command.walletId(), "wallet.etc.failed");
            idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        String codeHash = etcCodeSecurityService.hashCode(command.code());
        var etc = etcRepository.generateCode(command.walletId(), codeHash, command.expiresAt());
        AUDIT_LOG.info("event=wallet.etc.generated walletId={} codeRef={} expiresAt={}",
                command.walletId(),
            etcCodeSecurityService.redact(command.code()),
                command.expiresAt());
        eventPublisher.publish("wallet.etc.generated", java.util.Map.of(
                "walletId", command.walletId(),
            "codeRef", etcCodeSecurityService.redact(command.code()),
                "expiresAt", command.expiresAt()));

        WalletFlowResult result = WalletFlowResult.success(
                "ETC generated",
                command.walletId(),
                etc.getId(),
                "wallet.etc.generated");
        idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
        return result;
    }

    @Override
    @Transactional
    public WalletFlowResult redeem(String code, String idempotencyKey, String deviceId, String sourceIp) {
        String endpointScope = "wallet.etc.redeem";
        String userScope = "wallet-user";
        String payloadFingerprint = WalletIdempotencyService.hashPayloadValue(String.join("|",
                String.valueOf(code),
                String.valueOf(deviceId),
                String.valueOf(sourceIp)));

        WalletFlowResult duplicate = findDuplicate(idempotencyKey, userScope, endpointScope, payloadFingerprint, null);
        if (duplicate != null) {
            return duplicate;
        }

        String codeHash = etcCodeSecurityService.hashCode(code);
        if (etcBruteForceProtectionService.isBlocked(codeHash, deviceId, sourceIp)) {
            emitFraudEvent("ETC brute-force threshold exceeded", code, deviceId, sourceIp, null);
            WalletFlowResult result = WalletFlowResult.failure("ETC redemption temporarily blocked", null, "wallet.etc.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        var etc = etcRepository.findByCodeHashForUpdate(codeHash).orElse(null);
        if (etc == null) {
            boolean thresholdHit = etcBruteForceProtectionService.registerFailure(codeHash, deviceId, sourceIp);
            if (thresholdHit) {
                emitFraudEvent("ETC brute-force threshold exceeded", code, deviceId, sourceIp, null);
            }
            eventPublisher.publish("wallet.etc.failed", java.util.Map.of(
                    "reason", "ETC code not found",
                    "codeRef", etcCodeSecurityService.redact(code)));
            WalletFlowResult result = WalletFlowResult.failure("ETC code not found", null, "wallet.etc.failed");
                idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        if (etcRepository.isCodeExpired(codeHash, Instant.now())) {
            int attempts = etcRepository.registerFailedAttempt(codeHash, maxFailedAttemptsPerCode);
            boolean thresholdHit = etcBruteForceProtectionService.registerFailure(codeHash, deviceId, sourceIp);
            if (thresholdHit || attempts >= maxFailedAttemptsPerCode) {
                emitFraudEvent("ETC expired or brute-force attempts", code, deviceId, sourceIp, etc.getWallet().getId());
            }
            etcRepository.expireGeneratedCodes(Instant.now());
            WalletFlowResult result = WalletFlowResult.failure("ETC code has expired", etc.getWallet().getId(), "wallet.etc.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        boolean redeemed = etcRepository.redeemCode(codeHash, Instant.now());
        if (!redeemed) {
            int attempts = etcRepository.registerFailedAttempt(codeHash, maxFailedAttemptsPerCode);
            boolean thresholdHit = etcBruteForceProtectionService.registerFailure(codeHash, deviceId, sourceIp);
            if (thresholdHit || attempts >= maxFailedAttemptsPerCode) {
                emitFraudEvent("ETC brute-force threshold exceeded", code, deviceId, sourceIp, etc.getWallet().getId());
            }
            WalletFlowResult result = WalletFlowResult.failure("ETC code cannot be redeemed", etc.getWallet().getId(), "wallet.etc.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        etcBruteForceProtectionService.clearOnSuccess(codeHash, deviceId, sourceIp);

        Wallet wallet = walletRepository.findByIdForUpdate(etc.getWallet().getId()).orElse(null);
        if (wallet == null || wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            WalletFlowResult result = WalletFlowResult.failure("Wallet unavailable for ETC redemption", null, "wallet.etc.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        BigDecimal redeemAmount = resolveRedeemAmount(code);
        if (!limitEnforcementService.validate(wallet.getId(), WalletLimitEnforcementService.FlowType.ETC_REDEEM, redeemAmount)) {
            WalletFlowResult result = WalletFlowResult.failure("ETC redeem limits exceeded", wallet.getId(), "wallet.etc.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        wallet.setBalance(wallet.getBalance().add(redeemAmount));

        String externalReference = "etc-redeem-" + etcCodeSecurityService.redact(code);
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setType(Transaction.TransactionType.DEPOSIT);
        transaction.setAmount(redeemAmount);
        transaction.setReference(externalReference);
        transaction.setExternalReference(externalReference);
        transaction = transactionLifecycleService.initialize(transaction, "ETC redemption initiated", correlationId(), externalReference);
        transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.PENDING,
            "ETC redemption queued", correlationId(), null, null);
        transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.PROCESSING,
            "Redeeming ETC", correlationId(), null, null);

        ledgerIntegrationService.recordDoubleEntry("etc.redeem", wallet.getId(), redeemAmount, codeHash);

        AUDIT_LOG.info("event=wallet.etc.redeemed walletId={} codeRef={} amount={}", wallet.getId(), etcCodeSecurityService.redact(code), redeemAmount);
        eventPublisher.publish("wallet.etc.redeemed", java.util.Map.of(
                "walletId", wallet.getId(),
            "codeRef", etcCodeSecurityService.redact(code),
                "amount", redeemAmount));

        limitEnforcementService.record(wallet.getId(), WalletLimitEnforcementService.FlowType.ETC_REDEEM, redeemAmount);

        transactionLifecycleService.transition(transaction, Transaction.TransactionStatus.COMPLETED,
            "ETC redeemed", correlationId(), null, null);

        WalletFlowResult result = WalletFlowResult.success(
                "ETC redeemed",
                wallet.getId(),
                transaction.getId(),
                "wallet.etc.redeemed");
        idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
        return result;
    }

    private WalletFlowResult findDuplicate(String key,
                                           String userScope,
                                           String endpointScope,
                                           String payloadFingerprint,
                                           java.util.UUID walletId) {
        try {
            return idempotencyService.get(key, userScope, endpointScope, payloadFingerprint).orElse(null);
        } catch (RuntimeException ex) {
            return WalletFlowResult.failure(ex.getMessage(), walletId, "wallet.etc.failed");
        }
    }

    private String scope(java.util.UUID userId) {
        return userId == null ? "anonymous" : userId.toString();
    }

    private void emitFraudEvent(String reason, String code, String deviceId, String sourceIp, java.util.UUID walletId) {
        eventPublisher.publish("wallet.etc.fraud.detected", java.util.Map.of(
                "reason", reason,
                "walletId", walletId == null ? "unknown" : walletId,
                "codeRef", etcCodeSecurityService.redact(code),
                "deviceId", deviceId == null ? "unknown" : deviceId,
                "sourceIp", sourceIp == null ? "unknown" : sourceIp
        ));
    }

    private BigDecimal resolveRedeemAmount(String code) {
        if (code != null && code.startsWith("ETC-10")) {
            return new BigDecimal("10.00");
        }
        return BigDecimal.ONE;
    }

    private String correlationId() {
        return MDC.get("correlationId");
    }
}
