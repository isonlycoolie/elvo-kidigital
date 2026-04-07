package com.elvo.wallet.statemachine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletStateTransitionHandlers {

    private final WalletService walletService;

    public WalletStateTransitionHandlers(WalletService walletService) {
        this.walletService = walletService;
    }

    public WalletFlowResult reserveFunds(UUID walletId,
                                         UUID userId,
                                         BigDecimal amount,
                                         String idempotencyKey,
                                         String reference) {
        ReservationCommand command = new ReservationCommand(
                walletId,
                userId,
                amount,
                Instant.now().plus(30, ChronoUnit.MINUTES),
                idempotencyKey,
                reference);
        return walletService.createReservation(command);
    }

    public WalletFlowResult commitFunds(UUID reservationId, String idempotencyKey) {
        return walletService.confirmReservation(reservationId, idempotencyKey);
    }

    public WalletFlowResult rollbackFunds(UUID reservationId, String idempotencyKey) {
        return walletService.releaseReservation(reservationId, idempotencyKey);
    }
}
