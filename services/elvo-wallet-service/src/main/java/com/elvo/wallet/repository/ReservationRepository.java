package com.elvo.wallet.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.elvo.wallet.entity.Reservation;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, ReservationRepositoryCustom {

    List<Reservation> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    Optional<Reservation> findByIdAndWalletUserId(UUID reservationId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :reservationId")
    Optional<Reservation> findByIdForUpdate(@Param("reservationId") UUID reservationId);
}
