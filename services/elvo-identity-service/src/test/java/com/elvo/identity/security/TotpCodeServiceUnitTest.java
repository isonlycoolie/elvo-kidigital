package com.elvo.identity.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TotpCodeServiceUnitTest {

    @Test
    void generatedCodeShouldVerifyWithinAllowedWindow() {
        TotpCodeService service = new TotpCodeService(30, 6, 1);
        String secret = "JBSWY3DPEHPK3PXP";
        long counter = 123456;
        String code = service.generateAtCounter(secret, counter);

        Instant atTime = Instant.ofEpochSecond(counter * 30);
        assertTrue(service.verify(secret, code, atTime));
    }

    @Test
    void invalidCodeShouldFailVerification() {
        TotpCodeService service = new TotpCodeService(30, 6, 1);
        String secret = "JBSWY3DPEHPK3PXP";

        assertFalse(service.verify(secret, "000000", Instant.now()));
    }
}
