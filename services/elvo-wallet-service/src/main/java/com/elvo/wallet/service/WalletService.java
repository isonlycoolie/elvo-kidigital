package com.elvo.wallet.service;

import java.util.UUID;

import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.EtcCommand;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.TransferCommand;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;

public interface WalletService {

    WalletFlowResult processDeposit(DepositCommand command);

    WalletFlowResult processWithdrawal(WithdrawalCommand command);

    WalletFlowResult processTransfer(TransferCommand command);

    WalletFlowResult createReservation(ReservationCommand command);

    WalletFlowResult releaseReservation(UUID reservationId, String idempotencyKey);

    WalletFlowResult confirmReservation(UUID reservationId, String idempotencyKey);

    WalletFlowResult generateEtc(EtcCommand command);

    WalletFlowResult redeemEtc(String code, String idempotencyKey, String deviceId, String sourceIp);

    WalletFlowResult freezeWallet(UUID walletId, String reason);

    WalletFlowResult unfreezeWallet(UUID walletId, String reason);
}
