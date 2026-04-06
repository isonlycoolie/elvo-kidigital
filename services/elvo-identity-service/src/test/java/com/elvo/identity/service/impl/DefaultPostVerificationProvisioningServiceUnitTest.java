package com.elvo.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.elvo.identity.entity.User;

class DefaultPostVerificationProvisioningServiceUnitTest {

    @Test
    void provisioningShouldBeIdempotent() {
        DefaultPostVerificationProvisioningService service = new DefaultPostVerificationProvisioningService();
        User user = new User();

        service.provisionIfNeeded(user);
        Instant firstProvisionedAt = user.getDownstreamProvisionedAt();

        assertTrue(user.isDownstreamProvisioned());
        assertNotNull(firstProvisionedAt);

        service.provisionIfNeeded(user);

        assertEquals(firstProvisionedAt, user.getDownstreamProvisionedAt());
    }
}
