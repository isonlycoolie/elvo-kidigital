package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.elvo.wallet.dto.response.DelegatedWithdrawalTokenResponseDto;
import com.elvo.wallet.entity.DelegatedWithdrawalToken;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.DelegatedWithdrawalTokenRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.service.DelegatedWithdrawalTokenLifecycleService;

@Service
public class DelegatedWithdrawalTokenLifecycleServiceImpl implements DelegatedWithdrawalTokenLifecycleService {

    private final WalletRepository walletRepository;
    private final DelegatedWithdrawalTokenRepository tokenRepository;
    private final WalletFieldEncryptionService walletFieldEncryptionService;

    public DelegatedWithdrawalTokenLifecycleServiceImpl(WalletRepository walletRepository,
                                                        DelegatedWithdrawalTokenRepository tokenRepository,
                                                        WalletFieldEncryptionService walletFieldEncryptionService) {
        this.walletRepository = walletRepository;
        this.tokenRepository = tokenRepository;
        this.walletFieldEncryptionService = walletFieldEncryptionService;
    }

    @Override
    @Transactional
    public DelegatedWithdrawalTokenResponseDto issueToken(UUID userId,
                                                          UUID walletId,
                                                          BigDecimal amount,
                                                          Instant expiresAt,
                                                          String delegateReference,
                                                          String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
        if (!wallet.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found");
        }

        BigDecimal normalizedAmount = amount.setScale(4, RoundingMode.HALF_UP);
        if (normalizedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Token expiry must be in the future");
        }

        BigDecimal available = wallet.getBalance().subtract(wallet.getReservedBalance());
        if (available.compareTo(normalizedAmount) < 0) {
            throw new IllegalStateException("Insufficient available balance for delegated withdrawal token");
        }

        wallet.setReservedBalance(wallet.getReservedBalance().add(normalizedAmount));
        walletRepository.save(wallet);

        String rawToken = generateToken();
        DelegatedWithdrawalToken token = new DelegatedWithdrawalToken();
        token.setWalletId(walletId);
        token.setUserId(userId);
        token.setTokenReference(walletFieldEncryptionService.encrypt(rawToken));
        token.setDelegateReference(delegateReference.trim());
        token.setAmount(normalizedAmount);
        token.setStatus(DelegatedWithdrawalToken.Status.ISSUED);
        token.setExpiresAt(expiresAt);
        DelegatedWithdrawalToken saved = tokenRepository.save(token);

        return toResponse(saved, rawToken);
    }

    @Override
    @Transactional
    public DelegatedWithdrawalTokenResponseDto getToken(UUID userId,
                                                        UUID walletId,
                                                        String delegatedToken) {
        DelegatedWithdrawalToken token = findToken(userId, walletId, delegatedToken);
        maybeExpireToken(token);
        return toResponse(token, null);
    }

    @Override
    @Transactional
    public DelegatedWithdrawalTokenResponseDto cancelToken(UUID userId,
                                                           UUID walletId,
                                                           String delegatedToken,
                                                           String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }

        DelegatedWithdrawalToken token = findToken(userId, walletId, delegatedToken);
        maybeExpireToken(token);

        if (token.getStatus() == DelegatedWithdrawalToken.Status.ISSUED) {
            releaseReservedBalance(token);
            token.setStatus(DelegatedWithdrawalToken.Status.CANCELLED);
            tokenRepository.save(token);
        }

        return toResponse(token, null);
    }

    private DelegatedWithdrawalToken findToken(UUID userId, UUID walletId, String delegatedToken) {
        String encryptedReference = walletFieldEncryptionService.encrypt(delegatedToken);
        return tokenRepository.findByWalletIdAndUserIdAndTokenReference(walletId, userId, encryptedReference)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delegated withdrawal token not found"));
    }

    private void maybeExpireToken(DelegatedWithdrawalToken token) {
        if (token.getStatus() == DelegatedWithdrawalToken.Status.ISSUED && Instant.now().isAfter(token.getExpiresAt())) {
            releaseReservedBalance(token);
            token.setStatus(DelegatedWithdrawalToken.Status.EXPIRED);
            tokenRepository.save(token);
        }
    }

    private void releaseReservedBalance(DelegatedWithdrawalToken token) {
        Wallet wallet = walletRepository.findByIdForUpdate(token.getWalletId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        BigDecimal nextReserved = wallet.getReservedBalance().subtract(token.getAmount());
        if (nextReserved.signum() < 0) {
            nextReserved = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        wallet.setReservedBalance(nextReserved);
        walletRepository.save(wallet);
    }

    private DelegatedWithdrawalTokenResponseDto toResponse(DelegatedWithdrawalToken token, String rawToken) {
        return new DelegatedWithdrawalTokenResponseDto(
                token.getTokenId(),
                token.getWalletId(),
                token.getUserId(),
                rawToken,
                token.getDelegateReference(),
                token.getAmount(),
                token.getStatus().name(),
                token.getExpiresAt(),
                token.getCreatedAt(),
                token.getUpdatedAt()
        );
    }

    private String generateToken() {
        return ("DWT-" + UUID.randomUUID()).toUpperCase(Locale.ROOT);
    }
}
