package com.elvo.wallet.service;

import java.util.UUID;

import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

public interface ReservationFlowService {

    WalletFlowResult create(ReservationCommand command);

    WalletFlowResult release(UUID reservationId, String idempotencyKey);

    WalletFlowResult confirm(UUID reservationId, String idempotencyKey);
}
