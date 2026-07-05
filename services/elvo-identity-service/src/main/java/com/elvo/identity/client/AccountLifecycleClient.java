package com.elvo.identity.client;

import java.util.UUID;

public interface AccountLifecycleClient {

    void syncPostVerification(UUID userId, boolean emailVerified, boolean mobileVerified, String idempotencyKey);
}
