package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.elvo.wallet.entity.ChallengeCode;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.ChallengeCodeRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.ChallengeCodeSecurityService;
import com.elvo.wallet.service.ChallengeCodeService;

class DefaultChallengeCodeServiceTest {

    private ChallengeCodeRepository challengeCodeRepository;
    private WalletRepository walletRepository;
    private ChallengeCodeSecurityService challengeCodeSecurityService;
    private DefaultChallengeCodeService service;

    @BeforeEach
    void setUp() {
        challengeCodeRepository = mock(ChallengeCodeRepository.class);
        walletRepository = mock(WalletRepository.class);
        challengeCodeSecurityService = new ChallengeCodeSecurityService("sm://wallet-challenge-hash-pepper");
        service = new DefaultChallengeCodeService(
                challengeCodeRepository,
                walletRepository,
                challengeCodeSecurityService,
                5,
                15
        );
    }

    @Test
    void issueCodeShouldFailWhenWalletMissing() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.issueCode(walletId, Instant.now().plusSeconds(60), 1)
        );

        assertThat(ex.getMessage()).contains("Wallet not found");
    }

    @Test
    void validateCodeShouldFailForInvalidFormat() {
        ChallengeCodeService.ValidationResult result = service.validateCode(UUID.randomUUID(), "abc");
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("INVALID_CODE_FORMAT");
    }

    @Test
    void validateCodeShouldReturnConsumedWhenUsageExceeded() {
        UUID walletId = UUID.randomUUID();
        String code = "1234";
        String codeHash = challengeCodeSecurityService.hashCode(code);

        ChallengeCode entity = new ChallengeCode();
        Wallet wallet = new Wallet();
        wallet.setUserId(UUID.randomUUID());
        entity.setWallet(wallet);
        entity.setCodeHash(codeHash);
        entity.setUsageCount(1);
        entity.setMaxUsageCount(1);
        entity.setExpiresAt(Instant.now().plusSeconds(300));
        entity.setStatus(ChallengeCode.ChallengeCodeStatus.ACTIVE);

        when(challengeCodeRepository.findByCodeHashAndWalletIdForUpdate(codeHash, walletId)).thenReturn(Optional.of(entity));
        when(challengeCodeRepository.save(any(ChallengeCode.class))).thenReturn(entity);

        ChallengeCodeService.ValidationResult result = service.validateCode(walletId, code);
        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("CODE_CONSUMED");
    }
}
