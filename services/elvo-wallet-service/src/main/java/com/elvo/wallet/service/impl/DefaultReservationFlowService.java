package com.elvo.wallet.service.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Reservation;
import com.elvo.wallet.repository.ReservationRepository;
import com.elvo.wallet.service.ReservationFlowService;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultReservationFlowService implements ReservationFlowService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.flow.reservation");

    private final ReservationRepository reservationRepository;
    private final WalletIdempotencyService idempotencyService;
    private final WalletLedgerIntegrationService ledgerIntegrationService;
    private final WalletLimitEnforcementService limitEnforcementService;

    public DefaultReservationFlowService(ReservationRepository reservationRepository,
                                         WalletIdempotencyService idempotencyService,
                                         WalletLedgerIntegrationService ledgerIntegrationService,
                                         WalletLimitEnforcementService limitEnforcementService) {
        this.reservationRepository = reservationRepository;
        this.idempotencyService = idempotencyService;
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.limitEnforcementService = limitEnforcementService;
    }

    @Override
    @Transactional
    public WalletFlowResult create(ReservationCommand command) {
        if (command == null || command.walletId() == null || command.amount() == null || command.expiryDate() == null) {
            return WalletFlowResult.failure("Invalid reservation request", null, "wallet.reservation.failed");
        }

        WalletFlowResult duplicate = idempotencyService.get(command.idempotencyKey()).orElse(null);
        if (duplicate != null) {
            return duplicate;
        }

        if (!limitEnforcementService.validate(command.walletId(), WalletLimitEnforcementService.FlowType.RESERVATION, command.amount())) {
            WalletFlowResult result = WalletFlowResult.failure("Reservation limits exceeded", command.walletId(), "wallet.reservation.failed");
            idempotencyService.put(command.idempotencyKey(), result);
            return result;
        }

        Reservation reservation = reservationRepository.createReservation(command.walletId(), command.amount(), command.expiryDate());
        ledgerIntegrationService.recordDoubleEntry("reservation.create", command.walletId(), command.amount(), command.reference());
        AUDIT_LOG.info("event=wallet.reservation.created walletId={} reservationId={} amount={}",
                command.walletId(),
                reservation.getId(),
                command.amount());

        limitEnforcementService.record(command.walletId(), WalletLimitEnforcementService.FlowType.RESERVATION, command.amount());

        WalletFlowResult result = WalletFlowResult.success(
                "Reservation created",
                command.walletId(),
                reservation.getId(),
                "wallet.reservation.created");
        idempotencyService.put(command.idempotencyKey(), result);
        return result;
    }

    @Override
    @Transactional
    public WalletFlowResult release(UUID reservationId, String idempotencyKey) {
        WalletFlowResult duplicate = idempotencyService.get(idempotencyKey).orElse(null);
        if (duplicate != null) {
            return duplicate;
        }

        boolean released = reservationRepository.releaseReservation(reservationId);
        if (!released) {
            WalletFlowResult result = WalletFlowResult.failure("Reservation release failed", null, "wallet.reservation.failed");
            idempotencyService.put(idempotencyKey, result);
            return result;
        }

        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        UUID walletId = reservation != null && reservation.getWallet() != null ? reservation.getWallet().getId() : null;

        if (reservation != null) {
            ledgerIntegrationService.recordDoubleEntry("reservation.release", walletId, reservation.getAmount(), String.valueOf(reservationId));
        }

        AUDIT_LOG.info("event=wallet.reservation.released reservationId={} walletId={}", reservationId, walletId);

        WalletFlowResult result = WalletFlowResult.success(
                "Reservation released",
                walletId,
                reservationId,
                "wallet.reservation.released");
        idempotencyService.put(idempotencyKey, result);
        return result;
    }

    @Override
    @Transactional
    public WalletFlowResult confirm(UUID reservationId, String idempotencyKey) {
        WalletFlowResult duplicate = idempotencyService.get(idempotencyKey).orElse(null);
        if (duplicate != null) {
            return duplicate;
        }

        boolean confirmed = reservationRepository.confirmDebit(reservationId);
        if (!confirmed) {
            WalletFlowResult result = WalletFlowResult.failure("Reservation confirm failed", null, "wallet.reservation.failed");
            idempotencyService.put(idempotencyKey, result);
            return result;
        }

        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        UUID walletId = reservation != null && reservation.getWallet() != null ? reservation.getWallet().getId() : null;
        if (reservation != null) {
            ledgerIntegrationService.recordDoubleEntry("reservation.confirm", walletId, reservation.getAmount(), String.valueOf(reservationId));
        }

        AUDIT_LOG.info("event=wallet.reservation.confirmed reservationId={} walletId={}", reservationId, walletId);

        WalletFlowResult result = WalletFlowResult.success(
                "Reservation confirmed",
                walletId,
                reservationId,
                "wallet.reservation.confirmed");
        idempotencyService.put(idempotencyKey, result);
        return result;
    }
}
