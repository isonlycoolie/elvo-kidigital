package com.elvo.wallet.service.model;

import java.util.UUID;

public record WalletFlowResult(boolean success, String message, UUID walletId, UUID transactionId, String eventType) {

    public static WalletFlowResult success(String message, UUID walletId, UUID transactionId, String eventType) {
        return new WalletFlowResult(true, message, walletId, transactionId, eventType);
    }

    public static WalletFlowResult failure(String message, UUID walletId, String eventType) {
        return new WalletFlowResult(false, message, walletId, null, eventType);
    }
}
