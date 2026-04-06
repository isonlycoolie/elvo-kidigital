package com.elvo.wallet.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.elvo.wallet.service.model.WithdrawalMode;

class StepUpAuthenticationServiceUnitTest {

    private StepUpAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new StepUpAuthenticationService(new BigDecimal("500.00"), new BigDecimal("250.00"));
    }

    @Test
    void shouldRequireStepUpForHighValueWithdrawal() {
        assertTrue(service.requiresStepUpForWithdrawal(new BigDecimal("250.00"), WithdrawalMode.REGISTERED_NUMBER));
        assertTrue(service.requiresStepUpForWithdrawal(new BigDecimal("900.00"), WithdrawalMode.REGISTERED_NUMBER));
    }

    @Test
    void shouldRequireStepUpForDeviceFreeAndOtherNumberWithdrawal() {
        assertTrue(service.requiresStepUpForWithdrawal(new BigDecimal("10.00"), WithdrawalMode.DEVICE_FREE));
        assertTrue(service.requiresStepUpForWithdrawal(new BigDecimal("10.00"), WithdrawalMode.OTHER_NUMBER));
    }

    @Test
    void shouldNotRequireStepUpForLowValueSelfWithdrawal() {
        assertFalse(service.requiresStepUpForWithdrawal(new BigDecimal("249.99"), WithdrawalMode.REGISTERED_NUMBER));
    }

    @Test
    void shouldRequireStepUpForHighValueTransfer() {
        assertTrue(service.requiresStepUpForTransfer(new BigDecimal("500.00")));
        assertTrue(service.requiresStepUpForTransfer(new BigDecimal("999.00")));
        assertFalse(service.requiresStepUpForTransfer(new BigDecimal("499.99")));
    }

    @Test
    void shouldValidateStepUpChallengeBinding() {
        assertTrue(service.isValidConfirmation("pin", "token:c-123:ok", "c-123"));
        assertFalse(service.isValidConfirmation("pin", "token:other", "c-123"));
        assertFalse(service.isValidConfirmation("invalid", "token:c-123", "c-123"));
    }
}
