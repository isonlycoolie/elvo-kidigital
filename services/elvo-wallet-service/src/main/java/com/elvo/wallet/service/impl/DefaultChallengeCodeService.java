package com.elvo.wallet.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.ChallengeCode;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.ChallengeCodeRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.ChallengeCodeSecurityService;
import com.elvo.wallet.service.ChallengeCodeService;

@Service
public class DefaultChallengeCodeService implements ChallengeCodeService {

    private final ChallengeCodeRepository challengeCodeRepository;
    private final WalletRepository walletRepository;
    private final ChallengeCodeSecurityService challengeCodeSecurityService;
    private final int maxFailedAttempts;
    private final long lockMinutes;

    public DefaultChallengeCodeService(ChallengeCodeRepository challengeCodeRepository,
                                       WalletRepository walletRepository,
                                       ChallengeCodeSecurityService challengeCodeSecurityService,
                                       @Value("${elvo.security.challenge.max-failed-attempts:5}") int maxFailedAttempts,
                                       @Value("${elvo.security.challenge.lock-minutes:15}") long lockMinutes) {
        this.challengeCodeRepository = challengeCodeRepository;
        this.walletRepository = walletRepository;
        this.challengeCodeSecurityService = challengeCodeSecurityService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
    }

    @Override
    @Transactional
    public IssueResult issueCode(UUID walletId, Instant expiresAt, int maxUsageCount) {
        if (walletId == null || expiresAt == null) {
            throw new IllegalArgumentException("walletId and expiresAt are required");
        }
        if (maxUsageCount <= 0) {
            throw new IllegalArgumentException("maxUsageCount must be greater than zero");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            throw new IllegalStateException("Wallet is frozen");
        }

        String plainCode = challengeCodeSecurityService.generateCode();
        String codeHash = challengeCodeSecurityService.hashCode(plainCode);

        ChallengeCode challengeCode = new ChallengeCode();
        challengeCode.setWallet(wallet);
        challengeCode.setCodeHash(codeHash);
        challengeCode.setExpiresAt(expiresAt);
        challengeCode.setMaxUsageCount(maxUsageCount);
        challengeCode.setUsageCount(0);
        challengeCode.setFailedAttemptCount(0);
        challengeCode.setStatus(ChallengeCode.ChallengeCodeStatus.ACTIVE);

        ChallengeCode saved = challengeCodeRepository.save(challengeCode);
        return new IssueResult(saved.getId(), plainCode, saved.getExpiresAt());
    }

    @Override
    @Transactional
    public ValidationResult validateCode(UUID walletId, String rawCode) {
        if (walletId == null || !challengeCodeSecurityService.isValidFormat(rawCode)) {
            return ValidationResult.fail("INVALID_CODE_FORMAT");
        }

        String codeHash = challengeCodeSecurityService.hashCode(rawCode);
        ChallengeCode challengeCode = challengeCodeRepository
                .findByCodeHashAndWalletIdForUpdate(codeHash, walletId)
                .orElse(null);

        if (challengeCode == null) {
            return ValidationResult.fail("CODE_NOT_FOUND");
        }

        Instant now = Instant.now();
        if (challengeCode.getExpiresAt() != null && challengeCode.getExpiresAt().isBefore(now)) {
            challengeCode.setStatus(ChallengeCode.ChallengeCodeStatus.EXPIRED);
            challengeCodeRepository.save(challengeCode);
            return ValidationResult.fail("CODE_EXPIRED");
        }

        if (challengeCode.getLockedUntil() != null && challengeCode.getLockedUntil().isAfter(now)) {
            challengeCode.setStatus(ChallengeCode.ChallengeCodeStatus.LOCKED);
            challengeCodeRepository.save(challengeCode);
            return ValidationResult.fail("CODE_LOCKED");
        }

        if (challengeCode.getUsageCount() >= challengeCode.getMaxUsageCount()) {
            challengeCode.setStatus(ChallengeCode.ChallengeCodeStatus.CONSUMED);
            challengeCodeRepository.save(challengeCode);
            return ValidationResult.fail("CODE_CONSUMED");
        }

        challengeCode.setUsageCount(challengeCode.getUsageCount() + 1);
        if (challengeCode.getUsageCount() >= challengeCode.getMaxUsageCount()) {
            challengeCode.setStatus(ChallengeCode.ChallengeCodeStatus.CONSUMED);
        }
        challengeCodeRepository.save(challengeCode);
        return ValidationResult.ok();
    }

    @Override
    @Transactional
    public int expireCodes(Instant now) {
        Instant effectiveNow = now == null ? Instant.now() : now;
        return challengeCodeRepository.expireActiveCodes(effectiveNow);
    }

    @Transactional
    public ValidationResult registerFailedAttempt(UUID walletId, String rawCode) {
        if (walletId == null || !challengeCodeSecurityService.isValidFormat(rawCode)) {
            return ValidationResult.fail("INVALID_CODE_FORMAT");
        }

        String codeHash = challengeCodeSecurityService.hashCode(rawCode);
        ChallengeCode challengeCode = challengeCodeRepository
                .findByCodeHashAndWalletIdForUpdate(codeHash, walletId)
                .orElse(null);
        if (challengeCode == null) {
            return ValidationResult.fail("CODE_NOT_FOUND");
        }

        int attempts = challengeCode.getFailedAttemptCount() + 1;
        challengeCode.setFailedAttemptCount(attempts);
        if (attempts >= maxFailedAttempts) {
            challengeCode.setStatus(ChallengeCode.ChallengeCodeStatus.LOCKED);
            challengeCode.setLockedUntil(Instant.now().plusSeconds(lockMinutes * 60));
        }
        challengeCodeRepository.save(challengeCode);
        return ValidationResult.fail(attempts >= maxFailedAttempts ? "CODE_LOCKED" : "INVALID_CODE");
    }
}
