package com.elvo.wallet.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.dto.response.DelegatedWithdrawalTokenResponseDto;
import com.elvo.wallet.entity.DelegatedWithdrawalToken;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.DelegatedWithdrawalTokenRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.WalletFieldEncryptionService;

@ExtendWith(MockitoExtension.class)
class DelegatedWithdrawalTokenLifecycleServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DelegatedWithdrawalTokenRepository tokenRepository;

    @Mock
    private WalletFieldEncryptionService walletFieldEncryptionService;

    @InjectMocks
    private DelegatedWithdrawalTokenLifecycleServiceImpl service;

    @Captor
    private ArgumentCaptor<Wallet> walletCaptor;

    @Captor
    private ArgumentCaptor<DelegatedWithdrawalToken> tokenCaptor;

    private UUID userId;
    private UUID walletId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletId = UUID.randomUUID();

        wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("100.0000"));
        wallet.setReservedBalance(new BigDecimal("10.0000"));
    }

    @Test
    void issueTokenShouldReserveFundsAndReturnRawToken() {
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(walletFieldEncryptionService.encrypt(any(String.class))).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(tokenRepository.save(any(DelegatedWithdrawalToken.class))).thenAnswer(invocation -> {
            DelegatedWithdrawalToken token = invocation.getArgument(0);
            token.setStatus(DelegatedWithdrawalToken.Status.ISSUED);
            return token;
        });

        DelegatedWithdrawalTokenResponseDto response = service.issueToken(
                userId,
                walletId,
                new BigDecimal("15.0000"),
                Instant.now().plusSeconds(300),
                "AGENT-001",
                "idem-12345");

        assertNotNull(response.getDelegatedToken());
        assertEquals("ISSUED", response.getStatus());

        verify(walletRepository).save(walletCaptor.capture());
        assertEquals(new BigDecimal("25.0000"), walletCaptor.getValue().getReservedBalance());

        verify(tokenRepository).save(tokenCaptor.capture());
        assertEquals(walletId, tokenCaptor.getValue().getWalletId());
        assertEquals(userId, tokenCaptor.getValue().getUserId());
        assertEquals(new BigDecimal("15.0000"), tokenCaptor.getValue().getAmount());
    }

    @Test
    void getTokenShouldExpireIssuedTokenAndReleaseReservationWhenPastExpiry() {
        DelegatedWithdrawalToken token = new DelegatedWithdrawalToken();
        token.setWalletId(walletId);
        token.setUserId(userId);
        token.setAmount(new BigDecimal("20.0000"));
        token.setStatus(DelegatedWithdrawalToken.Status.ISSUED);
        token.setExpiresAt(Instant.now().minusSeconds(10));
        token.setTokenReference("enc:raw-token");

        when(walletFieldEncryptionService.encrypt("raw-token")).thenReturn("enc:raw-token");
        when(tokenRepository.findByWalletIdAndUserIdAndTokenReference(walletId, userId, "enc:raw-token"))
                .thenReturn(Optional.of(token));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));

        DelegatedWithdrawalTokenResponseDto response = service.getToken(userId, walletId, "raw-token");

        assertEquals("EXPIRED", response.getStatus());
        verify(walletRepository, times(1)).save(walletCaptor.capture());
        assertEquals(new BigDecimal("0.0000"), walletCaptor.getValue().getReservedBalance());
    }

    @Test
    void cancelTokenShouldReleaseReservationAndMarkCancelled() {
        DelegatedWithdrawalToken token = new DelegatedWithdrawalToken();
        token.setWalletId(walletId);
        token.setUserId(userId);
        token.setAmount(new BigDecimal("12.0000"));
        token.setStatus(DelegatedWithdrawalToken.Status.ISSUED);
        token.setExpiresAt(Instant.now().plusSeconds(300));
        token.setTokenReference("enc:cancel-token");

        when(walletFieldEncryptionService.encrypt("cancel-token")).thenReturn("enc:cancel-token");
        when(tokenRepository.findByWalletIdAndUserIdAndTokenReference(walletId, userId, "enc:cancel-token"))
                .thenReturn(Optional.of(token));
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));

        DelegatedWithdrawalTokenResponseDto response = service.cancelToken(userId, walletId, "cancel-token", "user request");

        assertEquals("CANCELLED", response.getStatus());
        verify(walletRepository).save(walletCaptor.capture());
        assertEquals(new BigDecimal("0.0000"), walletCaptor.getValue().getReservedBalance());
    }
}
