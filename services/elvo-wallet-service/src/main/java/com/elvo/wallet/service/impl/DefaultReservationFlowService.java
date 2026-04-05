package com.elvo.wallet.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.ReservationFlowService;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultReservationFlowService implements ReservationFlowService {

    @Override
    public WalletFlowResult create(ReservationCommand command) {
        return WalletFlowResult.failure("Reservation flow not initialized", command.walletId(), "wallet.reservation.failed");
    }

    @Override
    public WalletFlowResult release(UUID reservationId, String idempotencyKey) {
        return WalletFlowResult.failure("Reservation release flow not initialized", null, "wallet.reservation.release.failed");
    }

    @Override
    public WalletFlowResult confirm(UUID reservationId, String idempotencyKey) {
        return WalletFlowResult.failure("Reservation confirm flow not initialized", null, "wallet.reservation.confirm.failed");
    }
}
