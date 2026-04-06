package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.EtcRepository;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.EtcBruteForceProtectionService;
import com.elvo.wallet.security.EtcCodeSecurityService;
import com.elvo.wallet.security.EtcCodePolicyService;
import com.elvo.wallet.service.EtcFlowService;
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

        WalletFlowResult duplicate = idempotencyService.get(command.idempotencyKey()).orElse(null);
        if (duplicate != null) {
            return duplicate;
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
        idempotencyService.put(command.idempotencyKey(), result);
        return result;
    }

    @Override
    @Transactional
    public WalletFlowResult redeem(String code, String idempotencyKey, String deviceId, String sourceIp) {
        WalletFlowResult duplicate = idempotencyService.get(idempotencyKey).orElse(null);
        if (duplicate != null) {
            return duplicate;
        }

        String codeHash = etcCodeSecurityService.hashCode(code);
        if (etcBruteForceProtectionService.isBlocked(codeHash, deviceId, sourceIp)) {
            emitFraudEvent("ETC brute-force threshold exceeded", code, deviceId, sourceIp, null);
            WalletFlowResult result = WalletFlowResult.failure("ETC redemption temporarily blocked", null, "wallet.etc.failed");
            idempotencyService.put(idempotencyKey, result);
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
            idempotencyService.put(idempotencyKey, result);
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
            idempotencyService.put(idempotencyKey, result);
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
            idempotencyService.put(idempotencyKey, result);
            return result;
        }

        etcBruteForceProtectionService.clearOnSuccess(codeHash, deviceId, sourceIp);

        Wallet wallet = walletRepository.findByIdForUpdate(etc.getWallet().getId()).orElse(null);
        if (wallet == null || wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            WalletFlowResult result = WalletFlowResult.failure("Wallet unavailable for ETC redemption", null, "wallet.etc.failed");
            idempotencyService.put(idempotencyKey, result);
            return result;
        }

        BigDecimal redeemAmount = resolveRedeemAmount(code);
        if (!limitEnforcementService.validate(wallet.getId(), WalletLimitEnforcementService.FlowType.ETC_REDEEM, redeemAmount)) {
            WalletFlowResult result = WalletFlowResult.failure("ETC redeem limits exceeded", wallet.getId(), "wallet.etc.failed");
            idempotencyService.put(idempotencyKey, result);
            return result;
        }

        wallet.setBalance(wallet.getBalance().add(redeemAmount));

        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setType(Transaction.TransactionType.DEPOSIT);
        transaction.setAmount(redeemAmount);
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setReference("etc-redeem-" + etcCodeSecurityService.redact(code));
        transactionRepository.save(transaction);

        ledgerIntegrationService.recordDoubleEntry("etc.redeem", wallet.getId(), redeemAmount, codeHash);

        AUDIT_LOG.info("event=wallet.etc.redeemed walletId={} codeRef={} amount={}", wallet.getId(), etcCodeSecurityService.redact(code), redeemAmount);
        eventPublisher.publish("wallet.etc.redeemed", java.util.Map.of(
                "walletId", wallet.getId(),
            "codeRef", etcCodeSecurityService.redact(code),
                "amount", redeemAmount));

        limitEnforcementService.record(wallet.getId(), WalletLimitEnforcementService.FlowType.ETC_REDEEM, redeemAmount);

        WalletFlowResult result = WalletFlowResult.success(
                "ETC redeemed",
                wallet.getId(),
                transaction.getId(),
                "wallet.etc.redeemed");
        idempotencyService.put(idempotencyKey, result);
        return result;
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
}
