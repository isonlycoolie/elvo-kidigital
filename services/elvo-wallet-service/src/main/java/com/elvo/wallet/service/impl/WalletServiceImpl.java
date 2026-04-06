package com.elvo.wallet.service.impl;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import com.elvo.wallet.monitoring.WalletMetricsRecorder;
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
    private final WalletMetricsRecorder metricsRecorder;
    private final WalletFreezeValidationMiddleware walletFreezeValidationMiddleware;

    public WalletServiceImpl(DepositFlowService depositFlowService,
                             WithdrawalFlowService withdrawalFlowService,
                             TransferFlowService transferFlowService,
                             ReservationFlowService reservationFlowService,
                             EtcFlowService etcFlowService,
                             WalletLifecycleService walletLifecycleService,
                             WalletExecutionLockManager lockManager,
                             WalletMetricsRecorder metricsRecorder,
                             WalletFreezeValidationMiddleware walletFreezeValidationMiddleware) {
        this.depositFlowService = depositFlowService;
        this.withdrawalFlowService = withdrawalFlowService;
        this.transferFlowService = transferFlowService;
        this.reservationFlowService = reservationFlowService;
        this.etcFlowService = etcFlowService;
        this.walletLifecycleService = walletLifecycleService;
        this.lockManager = lockManager;
        this.metricsRecorder = metricsRecorder;
        this.walletFreezeValidationMiddleware = walletFreezeValidationMiddleware;
    }

    @Override
    public WalletFlowResult processDeposit(DepositCommand command) {
        WalletFlowResult blocked = walletFreezeValidationMiddleware
                .validateOperable(command.walletId(), "wallet.deposit.failed")
                .orElse(null);
        if (blocked != null) {
            return blocked;
        }
        WalletFlowResult result = withWalletLock(command.walletId(), () -> depositFlowService.process(command));
        metricsRecorder.recordTransaction("deposit", result.success());
        if (result.success()) {
            metricsRecorder.recordBalanceChange("deposit", "credit", command.amount());
        }
        return result;
    }

    @Override
    public WalletFlowResult processWithdrawal(WithdrawalCommand command) {
        WalletFlowResult blocked = walletFreezeValidationMiddleware
                .validateOperable(command.walletId(), "wallet.withdrawal.failed")
                .orElse(null);
        if (blocked != null) {
            return blocked;
        }
        WalletFlowResult result = withWalletLock(command.walletId(), () -> withdrawalFlowService.process(command));
        metricsRecorder.recordTransaction("withdrawal", result.success());
        if (result.success()) {
            metricsRecorder.recordBalanceChange("withdrawal", "debit", command.amount());
        }
        return result;
    }

    @Override
    public WalletFlowResult processTransfer(TransferCommand command) {
        WalletFlowResult sourceBlocked = walletFreezeValidationMiddleware
                .validateOperable(command.sourceWalletId(), "wallet.transfer.failed")
                .orElse(null);
        if (sourceBlocked != null) {
            return sourceBlocked;
        }
        WalletFlowResult targetBlocked = walletFreezeValidationMiddleware
                .validateOperable(command.targetWalletId(), "wallet.transfer.failed")
                .orElse(null);
        if (targetBlocked != null) {
            return targetBlocked;
        }

        UUID left = command.sourceWalletId().compareTo(command.targetWalletId()) <= 0
                ? command.sourceWalletId()
                : command.targetWalletId();
        UUID right = left.equals(command.sourceWalletId()) ? command.targetWalletId() : command.sourceWalletId();

        WalletFlowResult result = withWalletLock(left, () -> withWalletLock(right, () -> transferFlowService.process(command)));
        metricsRecorder.recordTransaction("transfer", result.success());
        if (result.success()) {
            metricsRecorder.recordBalanceChange("transfer", "debit", command.amount());
            metricsRecorder.recordBalanceChange("transfer", "credit", command.amount());
        }
        return result;
    }

    @Override
    public WalletFlowResult createReservation(ReservationCommand command) {
        WalletFlowResult blocked = walletFreezeValidationMiddleware
                .validateOperable(command.walletId(), "wallet.reservation.failed")
                .orElse(null);
        if (blocked != null) {
            return blocked;
        }
        WalletFlowResult result = withWalletLock(command.walletId(), () -> reservationFlowService.create(command));
        metricsRecorder.recordReservation("create", result.success());
        if (result.success()) {
            metricsRecorder.recordBalanceChange("reservation", "reserve", command.amount());
        }
        return result;
    }

    @Override
    public WalletFlowResult releaseReservation(UUID reservationId, String idempotencyKey) {
        WalletFlowResult result = reservationFlowService.release(reservationId, idempotencyKey);
        metricsRecorder.recordReservation("release", result.success());
        return result;
    }

    @Override
    public WalletFlowResult confirmReservation(UUID reservationId, String idempotencyKey) {
        WalletFlowResult result = reservationFlowService.confirm(reservationId, idempotencyKey);
        metricsRecorder.recordReservation("confirm", result.success());
        return result;
    }

    @Override
    public WalletFlowResult generateEtc(EtcCommand command) {
        WalletFlowResult blocked = walletFreezeValidationMiddleware
                .validateOperable(command.walletId(), "wallet.etc.failed")
                .orElse(null);
        if (blocked != null) {
            return blocked;
        }
        WalletFlowResult result = withWalletLock(command.walletId(), () -> etcFlowService.generate(command));
        metricsRecorder.recordTransaction("etc_generate", result.success());
        return result;
    }

    @Override
    public WalletFlowResult redeemEtc(String code, String idempotencyKey, String deviceId, String sourceIp) {
        WalletFlowResult result = etcFlowService.redeem(code, idempotencyKey, deviceId, sourceIp);
        metricsRecorder.recordTransaction("etc_redeem", result.success());
        return result;
    }

    @Override
    public WalletFlowResult freezeWallet(UUID walletId, String reason) {
        WalletFlowResult result = withWalletLock(walletId, () -> walletLifecycleService.freeze(walletId, reason));
        metricsRecorder.recordFreezeAction("freeze", result.success());
        return result;
    }

    @Override
    public WalletFlowResult unfreezeWallet(UUID walletId, String reason) {
        WalletFlowResult result = withWalletLock(walletId, () -> walletLifecycleService.unfreeze(walletId, reason));
        metricsRecorder.recordFreezeAction("unfreeze", result.success());
        return result;
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
