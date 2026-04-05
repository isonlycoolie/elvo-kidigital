package com.elvo.wallet.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Reservation;
import com.elvo.wallet.entity.Wallet;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Repository
@Transactional
public class ReservationRepositoryImpl implements ReservationRepositoryCustom {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.repository");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Reservation createReservation(UUID walletId, BigDecimal amount, Instant expiryDate) {
        validateCreateInputs(walletId, amount, expiryDate);

        Wallet wallet = entityManager.find(Wallet.class, walletId, LockModeType.PESSIMISTIC_WRITE);
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet not found for id: " + walletId);
        }

        BigDecimal available = wallet.getBalance().subtract(wallet.getReservedBalance());
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient available balance for reservation");
        }

        wallet.setReservedBalance(wallet.getReservedBalance().add(amount));

        Reservation reservation = new Reservation();
        reservation.setWallet(wallet);
        reservation.setAmount(amount);
        reservation.setExpiryDate(expiryDate);
        reservation.setStatus(Reservation.ReservationStatus.CREATED);
        entityManager.persist(reservation);

        AUDIT_LOG.info("reservation_created reservationId={} walletId={} amount={} expiryDate={}",
                reservation.getId(),
                walletId,
                amount,
                expiryDate);

        return reservation;
    }

    @Override
    public boolean releaseReservation(UUID reservationId) {
        Reservation reservation = entityManager.find(Reservation.class, reservationId, LockModeType.PESSIMISTIC_WRITE);
        if (reservation == null || reservation.getStatus() != Reservation.ReservationStatus.CREATED) {
            return false;
        }

        Wallet wallet = entityManager.find(Wallet.class, reservation.getWallet().getId(), LockModeType.PESSIMISTIC_WRITE);
        BigDecimal updatedReservedBalance = wallet.getReservedBalance().subtract(reservation.getAmount());
        if (updatedReservedBalance.signum() < 0) {
            updatedReservedBalance = BigDecimal.ZERO;
        }

        wallet.setReservedBalance(updatedReservedBalance);
        reservation.setStatus(Reservation.ReservationStatus.RELEASED);

        AUDIT_LOG.info("reservation_released reservationId={} walletId={} amount={}",
                reservationId,
                wallet.getId(),
                reservation.getAmount());

        return true;
    }

    @Override
    public boolean confirmDebit(UUID reservationId) {
        Reservation reservation = entityManager.find(Reservation.class, reservationId, LockModeType.PESSIMISTIC_WRITE);
        if (reservation == null || reservation.getStatus() != Reservation.ReservationStatus.CREATED) {
            return false;
        }

        Wallet wallet = entityManager.find(Wallet.class, reservation.getWallet().getId(), LockModeType.PESSIMISTIC_WRITE);
        BigDecimal amount = reservation.getAmount();

        if (wallet.getReservedBalance().compareTo(amount) < 0 || wallet.getBalance().compareTo(amount) < 0) {
            AUDIT_LOG.warn("reservation_confirm_rejected reservationId={} walletId={} reason=insufficient_funds",
                    reservationId,
                    wallet.getId());
            return false;
        }

        wallet.setReservedBalance(wallet.getReservedBalance().subtract(amount));
        wallet.setBalance(wallet.getBalance().subtract(amount));
        reservation.setStatus(Reservation.ReservationStatus.CONFIRMED);

        AUDIT_LOG.info("reservation_confirmed reservationId={} walletId={} amount={} newBalance={}",
                reservationId,
                wallet.getId(),
                amount,
                wallet.getBalance());

        return true;
    }

    @Override
    public int expireReservations(Instant currentTime) {
        Objects.requireNonNull(currentTime, "currentTime must not be null");

        List<UUID> expiredReservationIds = entityManager.createQuery(
                        "select r.id from Reservation r where r.status = :status and r.expiryDate <= :currentTime",
                        UUID.class)
                .setParameter("status", Reservation.ReservationStatus.CREATED)
                .setParameter("currentTime", currentTime)
                .getResultList();

        int releasedCount = 0;
        for (UUID reservationId : expiredReservationIds) {
            if (releaseReservation(reservationId)) {
                releasedCount++;
            }
        }

        AUDIT_LOG.info("reservation_expiry_processed count={} at={}", releasedCount, currentTime);
        return releasedCount;
    }

    private static void validateCreateInputs(UUID walletId, BigDecimal amount, Instant expiryDate) {
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(expiryDate, "expiryDate must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Reservation amount must be greater than zero");
        }
    }
}
