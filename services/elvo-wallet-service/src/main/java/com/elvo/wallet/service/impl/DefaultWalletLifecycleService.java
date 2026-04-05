package com.elvo.wallet.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.WalletLifecycleService;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultWalletLifecycleService implements WalletLifecycleService {

    @Override
    public WalletFlowResult freeze(UUID walletId, String reason) {
        return WalletFlowResult.failure("Freeze flow not initialized", walletId, "wallet.wallet.frozen.failed");
    }

    @Override
    public WalletFlowResult unfreeze(UUID walletId, String reason) {
        return WalletFlowResult.failure("Unfreeze flow not initialized", walletId, "wallet.wallet.unfrozen.failed");
    }
}
