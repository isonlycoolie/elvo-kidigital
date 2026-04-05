package com.elvo.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import com.elvo.wallet.entity.Reservation;
import com.elvo.wallet.entity.Wallet;

@DataJpaTest
@ActiveProfiles("test")
class ReservationRepositoryImplTest {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void createReservationShouldIncreaseReservedBalance() {
        Wallet wallet = walletRepository.save(createWallet(new BigDecimal("100.00")));

        Reservation reservation = reservationRepository.createReservation(wallet.getId(), new BigDecimal("25.00"), Instant.now().plusSeconds(3600));

        assertThat(reservation.getId()).isNotNull();
        Wallet reloaded = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(reloaded.getReservedBalance()).isEqualByComparingTo("25.00");
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CREATED);
    }

    @Test
    void confirmDebitShouldDecreaseBalanceAndReservedBalance() {
        Wallet wallet = walletRepository.save(createWallet(new BigDecimal("100.00")));
        Reservation reservation = reservationRepository.createReservation(wallet.getId(), new BigDecimal("30.00"), Instant.now().plusSeconds(3600));

        boolean confirmed = reservationRepository.confirmDebit(reservation.getId());

        assertThat(confirmed).isTrue();
        Wallet reloaded = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("70.00");
        assertThat(reloaded.getReservedBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void createReservationShouldRejectInsufficientBalance() {
        Wallet wallet = walletRepository.save(createWallet(new BigDecimal("10.00")));

        assertThatThrownBy(() -> reservationRepository.createReservation(wallet.getId(), new BigDecimal("25.00"), Instant.now().plusSeconds(3600)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Insufficient available balance");
    }

    private Wallet createWallet(BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(balance);
        wallet.setReservedBalance(BigDecimal.ZERO);
        wallet.setStatus(Wallet.WalletStatus.ACTIVE);
        return wallet;
    }
}
