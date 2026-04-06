package com.elvo.wallet.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.ActiveProfiles;

import com.elvo.wallet.entity.Etc;
import com.elvo.wallet.entity.Wallet;

@DataJpaTest
@ActiveProfiles("test")
class EtcRepositoryImplTest {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private EtcRepository etcRepository;

    @Test
    void generateCodeShouldPersistEtc() {
        Wallet wallet = walletRepository.save(createWallet());
        String codeHash = "hash-etc-test-1";

        Etc etc = etcRepository.generateCode(wallet.getId(), codeHash, Instant.now().plusSeconds(3600));

        Etc reloaded = etcRepository.findByCodeHash(codeHash).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(etc.getId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(reloaded.getStatus()).isEqualTo(Etc.EtcStatus.GENERATED);
        org.assertj.core.api.Assertions.assertThat(reloaded.getWallet().getId()).isEqualTo(wallet.getId());
        org.assertj.core.api.Assertions.assertThat(reloaded.getCodeHash()).isEqualTo(codeHash);
    }

    @Test
    void redeemCodeShouldMarkEtcRedeemed() {
        Wallet wallet = walletRepository.save(createWallet());
        String codeHash = "hash-etc-test-2";
        etcRepository.generateCode(wallet.getId(), codeHash, Instant.now().plusSeconds(3600));

        boolean redeemed = etcRepository.redeemCode(codeHash, Instant.now());

        Etc reloaded = etcRepository.findByCodeHash(codeHash).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(redeemed).isTrue();
        org.assertj.core.api.Assertions.assertThat(reloaded.getStatus()).isEqualTo(Etc.EtcStatus.REDEEMED);
    }

    @Test
    void generateCodeShouldRejectBlankCode() {
        assertThatThrownBy(() -> etcRepository.generateCode(UUID.randomUUID(), "  ", Instant.now().plusSeconds(3600)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("ETC code hash must not be blank");
    }

    private Wallet createWallet() {
        Wallet wallet = new Wallet();
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(new java.math.BigDecimal("100.00"));
        wallet.setReservedBalance(java.math.BigDecimal.ZERO);
        wallet.setStatus(Wallet.WalletStatus.ACTIVE);
        return wallet;
    }
}
