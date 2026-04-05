package com.elvo.wallet.service;

import java.util.UUID;

import com.elvo.wallet.service.model.WalletFlowResult;

public interface WalletLifecycleService {

    WalletFlowResult freeze(UUID walletId, String reason);

    WalletFlowResult unfreeze(UUID walletId, String reason);
}
