package com.elvo.identity.client;

import java.util.UUID;

public interface WalletProvisioningClient {

    void createWallet(UUID userId, String idempotencyKey);
}
