package com.elvo.wallet.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class DefaultIdentityServiceClient implements IdentityServiceClient {

    @Override
    public boolean isUserActive(UUID userId) {
        return userId != null;
    }

    @Override
    public boolean verifyEsp(UUID userId, String espCode) {
        return userId != null && espCode != null && !espCode.isBlank();
    }

    @Override
    public boolean verifyEac(UUID userId, String eacCode) {
        return userId != null && eacCode != null && !eacCode.isBlank();
    }
}
