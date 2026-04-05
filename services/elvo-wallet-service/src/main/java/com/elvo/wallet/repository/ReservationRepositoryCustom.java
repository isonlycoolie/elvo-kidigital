package com.elvo.wallet.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.elvo.wallet.entity.Reservation;

public interface ReservationRepositoryCustom {

    Reservation createReservation(UUID walletId, BigDecimal amount, Instant expiryDate);

    boolean releaseReservation(UUID reservationId);

    boolean confirmDebit(UUID reservationId);

    int expireReservations(Instant currentTime);
}
