package com.elvo.wallet.client;

import java.util.UUID;

public interface IdentityServiceClient {

    boolean isUserActive(UUID userId);

    boolean verifyEsp(UUID userId, String espCode);

    boolean verifyEac(UUID userId, String eacCode);
}
