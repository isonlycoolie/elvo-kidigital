package com.elvo.wallet.security;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.elvo.wallet.entity.Reservation;

@Component
public class WalletReservationStateTransitionValidator {

    private static final EnumSet<Reservation.ReservationStatus> COMMIT_ALLOWED_FROM = EnumSet.of(Reservation.ReservationStatus.CREATED);
    private static final EnumSet<Reservation.ReservationStatus> ROLLBACK_ALLOWED_FROM = EnumSet.of(Reservation.ReservationStatus.CREATED);

    public boolean canCommit(Reservation.ReservationStatus status) {
        return status != null && COMMIT_ALLOWED_FROM.contains(status);
    }

    public boolean canRollback(Reservation.ReservationStatus status) {
        return status != null && ROLLBACK_ALLOWED_FROM.contains(status);
    }
}
