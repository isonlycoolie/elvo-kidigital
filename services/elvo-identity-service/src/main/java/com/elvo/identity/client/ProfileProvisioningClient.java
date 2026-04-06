package com.elvo.identity.client;

import java.util.UUID;

public interface ProfileProvisioningClient {

    void createProfile(UUID userId, String idempotencyKey);

    void createDefaultPreferences(UUID userId, String idempotencyKey);
}
