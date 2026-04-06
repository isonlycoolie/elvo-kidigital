package com.elvo.wallet.service.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Reservation;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.ReservationRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.service.ReservationFlowService;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultReservationFlowService implements ReservationFlowService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.flow.reservation");

    private final WalletRepository walletRepository;
    private final ReservationRepository reservationRepository;
    private final WalletIdempotencyService idempotencyService;
    private final WalletLedgerIntegrationService ledgerIntegrationService;
    private final WalletEventPublisher eventPublisher;
    private final WalletLimitEnforcementService limitEnforcementService;

    public DefaultReservationFlowService(WalletRepository walletRepository,
                                         ReservationRepository reservationRepository,
                                         WalletIdempotencyService idempotencyService,
                                         WalletLedgerIntegrationService ledgerIntegrationService,
                                         WalletEventPublisher eventPublisher,
                                         WalletLimitEnforcementService limitEnforcementService) {
        this.walletRepository = walletRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyService = idempotencyService;
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.eventPublisher = eventPublisher;
        this.limitEnforcementService = limitEnforcementService;
    }

    @Override
    @Transactional
    public WalletFlowResult create(ReservationCommand command) {
        if (command == null || command.walletId() == null || command.amount() == null || command.expiryDate() == null) {
            return WalletFlowResult.failure("Invalid reservation request", null, "wallet.reservation.failed");
        }

        String endpointScope = "wallet.reservation.create";
        String userScope = scope(command.userId());
        String payloadFingerprint = WalletIdempotencyService.hashPayloadValue(String.join("|",
                String.valueOf(command.walletId()),
                String.valueOf(command.userId()),
                String.valueOf(command.amount()),
                String.valueOf(command.expiryDate()),
                String.valueOf(command.reference())));

        WalletFlowResult duplicate = findDuplicate(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, command.walletId(), "wallet.reservation.failed");
        if (duplicate != null) {
            return duplicate;
        }

        Wallet wallet = walletRepository.findByIdForUpdate(command.walletId()).orElse(null);
        if (wallet == null) {
            WalletFlowResult result = WalletFlowResult.failure("Wallet not found", command.walletId(), "wallet.reservation.failed");
            idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            WalletFlowResult result = WalletFlowResult.failure("Wallet is frozen", command.walletId(), "wallet.reservation.failed");
            idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        if (!limitEnforcementService.validate(command.walletId(), WalletLimitEnforcementService.FlowType.RESERVATION, command.amount())) {
            WalletFlowResult result = WalletFlowResult.failure("Reservation limits exceeded", command.walletId(), "wallet.reservation.failed");
            idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        Reservation reservation = reservationRepository.createReservation(command.walletId(), command.amount(), command.expiryDate());
        ledgerIntegrationService.recordDoubleEntry("reservation.create", command.walletId(), command.amount(), command.reference());
        AUDIT_LOG.info("event=wallet.reservation.created walletId={} reservationId={} amount={}",
                command.walletId(),
                reservation.getId(),
                command.amount());
        eventPublisher.publish("wallet.reservation.created", java.util.Map.of(
                "walletId", command.walletId(),
                "reservationId", reservation.getId(),
                "amount", command.amount()));

        limitEnforcementService.record(command.walletId(), WalletLimitEnforcementService.FlowType.RESERVATION, command.amount());

        WalletFlowResult result = WalletFlowResult.success(
                "Reservation created",
                command.walletId(),
                reservation.getId(),
                "wallet.reservation.created");
        idempotencyService.put(command.idempotencyKey(), userScope, endpointScope, payloadFingerprint, result);
        return result;
    }

    @Override
    @Transactional
    public WalletFlowResult release(UUID reservationId, String idempotencyKey) {
        String endpointScope = "wallet.reservation.release";
        String userScope = "internal-service";
        String payloadFingerprint = WalletIdempotencyService.hashPayloadValue(String.valueOf(reservationId));
        WalletFlowResult duplicate = findDuplicate(idempotencyKey, userScope, endpointScope, payloadFingerprint, null, "wallet.reservation.failed");
        if (duplicate != null) {
            return duplicate;
        }

        boolean released = reservationRepository.releaseReservation(reservationId);
        if (!released) {
            WalletFlowResult result = WalletFlowResult.failure("Reservation release failed", null, "wallet.reservation.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        UUID walletId = reservation != null && reservation.getWallet() != null ? reservation.getWallet().getId() : null;

        if (reservation != null) {
            ledgerIntegrationService.recordDoubleEntry("reservation.release", walletId, reservation.getAmount(), String.valueOf(reservationId));
        }

        AUDIT_LOG.info("event=wallet.reservation.released reservationId={} walletId={}", reservationId, walletId);
        eventPublisher.publish("wallet.reservation.released", java.util.Map.of(
                "walletId", walletId,
                "reservationId", reservationId));

        WalletFlowResult result = WalletFlowResult.success(
                "Reservation released",
                walletId,
                reservationId,
                "wallet.reservation.released");
        idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
        return result;
    }

    @Override
    @Transactional
    public WalletFlowResult confirm(UUID reservationId, String idempotencyKey) {
        String endpointScope = "wallet.reservation.confirm";
        String userScope = "internal-service";
        String payloadFingerprint = WalletIdempotencyService.hashPayloadValue(String.valueOf(reservationId));
        WalletFlowResult duplicate = findDuplicate(idempotencyKey, userScope, endpointScope, payloadFingerprint, null, "wallet.reservation.failed");
        if (duplicate != null) {
            return duplicate;
        }

        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getWallet() == null) {
            WalletFlowResult result = WalletFlowResult.failure("Reservation not found", null, "wallet.reservation.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        if (reservation.getWallet().getStatus() == Wallet.WalletStatus.FROZEN) {
            WalletFlowResult result = WalletFlowResult.failure("Wallet is frozen", reservation.getWallet().getId(), "wallet.reservation.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        boolean confirmed = reservationRepository.confirmDebit(reservationId);
        if (!confirmed) {
            WalletFlowResult result = WalletFlowResult.failure("Reservation confirm failed", null, "wallet.reservation.failed");
            idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
            return result;
        }

        UUID walletId = reservation != null && reservation.getWallet() != null ? reservation.getWallet().getId() : null;
        if (reservation != null) {
            ledgerIntegrationService.recordDoubleEntry("reservation.confirm", walletId, reservation.getAmount(), String.valueOf(reservationId));
        }

        AUDIT_LOG.info("event=wallet.reservation.confirmed reservationId={} walletId={}", reservationId, walletId);
        eventPublisher.publish("wallet.reservation.confirmed", java.util.Map.of(
                "walletId", walletId,
                "reservationId", reservationId));

        WalletFlowResult result = WalletFlowResult.success(
                "Reservation confirmed",
                walletId,
                reservationId,
                "wallet.reservation.confirmed");
        idempotencyService.put(idempotencyKey, userScope, endpointScope, payloadFingerprint, result);
        return result;
    }

    private WalletFlowResult findDuplicate(String key,
                                           String userScope,
                                           String endpointScope,
                                           String payloadFingerprint,
                                           UUID walletId,
                                           String eventType) {
        try {
            return idempotencyService.get(key, userScope, endpointScope, payloadFingerprint).orElse(null);
        } catch (RuntimeException ex) {
            return WalletFlowResult.failure(ex.getMessage(), walletId, eventType);
        }
    }

    private String scope(UUID userId) {
        return userId == null ? "anonymous" : userId.toString();
    }
}
