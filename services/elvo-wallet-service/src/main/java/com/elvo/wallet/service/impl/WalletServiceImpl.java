package com.elvo.wallet.service.impl;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.DepositFlowService;
import com.elvo.wallet.service.EtcFlowService;
import com.elvo.wallet.service.ReservationFlowService;
import com.elvo.wallet.service.TransferFlowService;
import com.elvo.wallet.service.WalletLifecycleService;
import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.WithdrawalFlowService;
import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.EtcCommand;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.TransferCommand;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;

@Service
public class WalletServiceImpl implements WalletService {

    private final DepositFlowService depositFlowService;
    private final WithdrawalFlowService withdrawalFlowService;
    private final TransferFlowService transferFlowService;
    private final ReservationFlowService reservationFlowService;
    private final EtcFlowService etcFlowService;
    private final WalletLifecycleService walletLifecycleService;
    private final WalletExecutionLockManager lockManager;

    public WalletServiceImpl(DepositFlowService depositFlowService,
                             WithdrawalFlowService withdrawalFlowService,
                             TransferFlowService transferFlowService,
                             ReservationFlowService reservationFlowService,
                             EtcFlowService etcFlowService,
                             WalletLifecycleService walletLifecycleService,
                             WalletExecutionLockManager lockManager) {
        this.depositFlowService = depositFlowService;
        this.withdrawalFlowService = withdrawalFlowService;
        this.transferFlowService = transferFlowService;
        this.reservationFlowService = reservationFlowService;
        this.etcFlowService = etcFlowService;
        this.walletLifecycleService = walletLifecycleService;
        this.lockManager = lockManager;
    }

    @Override
    public WalletFlowResult processDeposit(DepositCommand command) {
        return withWalletLock(command.walletId(), () -> depositFlowService.process(command));
    }

    @Override
    public WalletFlowResult processWithdrawal(WithdrawalCommand command) {
        return withWalletLock(command.walletId(), () -> withdrawalFlowService.process(command));
    }

    @Override
    public WalletFlowResult processTransfer(TransferCommand command) {
        UUID left = command.sourceWalletId().compareTo(command.targetWalletId()) <= 0
                ? command.sourceWalletId()
                : command.targetWalletId();
        UUID right = left.equals(command.sourceWalletId()) ? command.targetWalletId() : command.sourceWalletId();

        return withWalletLock(left, () -> withWalletLock(right, () -> transferFlowService.process(command)));
    }

    @Override
    public WalletFlowResult createReservation(ReservationCommand command) {
        return withWalletLock(command.walletId(), () -> reservationFlowService.create(command));
    }

    @Override
    public WalletFlowResult releaseReservation(UUID reservationId, String idempotencyKey) {
        return reservationFlowService.release(reservationId, idempotencyKey);
    }

    @Override
    public WalletFlowResult confirmReservation(UUID reservationId, String idempotencyKey) {
        return reservationFlowService.confirm(reservationId, idempotencyKey);
    }

    @Override
    public WalletFlowResult generateEtc(EtcCommand command) {
        return withWalletLock(command.walletId(), () -> etcFlowService.generate(command));
    }

    @Override
    public WalletFlowResult redeemEtc(String code, String idempotencyKey) {
        return etcFlowService.redeem(code, idempotencyKey);
    }

    @Override
    public WalletFlowResult freezeWallet(UUID walletId, String reason) {
        return withWalletLock(walletId, () -> walletLifecycleService.freeze(walletId, reason));
    }

    @Override
    public WalletFlowResult unfreezeWallet(UUID walletId, String reason) {
        return withWalletLock(walletId, () -> walletLifecycleService.unfreeze(walletId, reason));
    }

    private WalletFlowResult withWalletLock(UUID walletId, Operation operation) {
        ReentrantLock lock = lockManager.lock(String.valueOf(walletId));
        try {
            return operation.execute();
        } finally {
            lockManager.unlock(String.valueOf(walletId), lock);
        }
    }

    @FunctionalInterface
    private interface Operation {
        WalletFlowResult execute();
    }
}
