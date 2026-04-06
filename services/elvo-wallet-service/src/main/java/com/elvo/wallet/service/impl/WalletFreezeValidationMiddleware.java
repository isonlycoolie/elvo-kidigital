package com.elvo.wallet.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletFreezeValidationMiddleware {

    private final WalletRepository walletRepository;

    public WalletFreezeValidationMiddleware(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public Optional<WalletFlowResult> validateOperable(UUID walletId, String eventType) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId).orElse(null);
        if (wallet == null) {
            return Optional.of(WalletFlowResult.failure("Wallet not found", walletId, eventType));
        }
        if (wallet.getStatus() == Wallet.WalletStatus.FROZEN) {
            return Optional.of(WalletFlowResult.failure("Wallet is frozen", walletId, eventType));
        }
        return Optional.empty();
    }
}