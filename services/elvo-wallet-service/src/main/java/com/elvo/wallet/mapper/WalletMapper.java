package com.elvo.wallet.mapper;

import com.elvo.wallet.dto.response.BalanceResponseDto;
import com.elvo.wallet.dto.response.ReservationResponseDto;
import com.elvo.wallet.dto.response.TransactionResponseDto;
import com.elvo.wallet.dto.response.EtcResponseDto;
import com.elvo.wallet.dto.response.FlowResultResponseDto;
import com.elvo.wallet.dto.response.WalletResponseDto;
import com.elvo.wallet.entity.Etc;
import com.elvo.wallet.entity.Reservation;
import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.service.model.WalletFlowResult;

import org.springframework.stereotype.Component;

@Component
public class WalletMapper {

    public WalletResponseDto toWalletResponseDto(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        return new WalletResponseDto(
            wallet.getId(),
            wallet.getUserId(),
            wallet.getBalance(),
            wallet.getReservedBalance(),
            wallet.getStatus() != null ? wallet.getStatus().toString() : null,
            wallet.getCreatedAt(),
            wallet.getUpdatedAt()
        );
    }

    public BalanceResponseDto toBalanceResponseDto(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        return new BalanceResponseDto(wallet.getBalance(), wallet.getReservedBalance());
    }

    public TransactionResponseDto toTransactionResponseDto(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        return new TransactionResponseDto(
            transaction.getId(),
            transaction.getWallet().getId(),
            transaction.getType() != null ? transaction.getType().toString() : null,
            transaction.getAmount(),
            transaction.getStatus() != null ? transaction.getStatus().toString() : null,
            transaction.getReference(),
            null,
            transaction.getCreatedAt(),
            transaction.getUpdatedAt()
        );
    }

    public ReservationResponseDto toReservationResponseDto(Reservation reservation) {
        if (reservation == null) {
            return null;
        }
        return new ReservationResponseDto(
            reservation.getId(),
            reservation.getWallet().getId(),
            reservation.getAmount(),
            reservation.getStatus() != null ? reservation.getStatus().toString() : null,
            reservation.getExpiryDate(),
            reservation.getCreatedAt(),
            reservation.getUpdatedAt()
        );
    }

    public EtcResponseDto toEtcResponseDto(Etc etc) {
        if (etc == null) {
            return null;
        }
        return new EtcResponseDto(
            etc.getId(),
            etc.getWallet().getId(),
            "REDACTED",
            etc.getStatus() != null ? etc.getStatus().toString() : null,
            etc.getExpiresAt(),
            etc.getCreatedAt()
        );
    }

    public FlowResultResponseDto toFlowResultResponseDto(WalletFlowResult result) {
        if (result == null) {
            return null;
        }
        return new FlowResultResponseDto(
            result.success(),
            result.message(),
            result.walletId(),
            result.transactionId(),
            result.eventType()
        );
    }
}
