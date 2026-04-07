package com.elvo.billing.statemachine;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingRetryMechanismTest {

    @Mock
    private BillingStateTransitionHandlers stateTransitionHandlers;

    @Test
    void shouldReturnSuccessfulResponseImmediately() {
        BillingRetryMechanism retryMechanism = new BillingRetryMechanism(stateTransitionHandlers, 3, 15);
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();

        PaymentResponseDto success = new PaymentResponseDto();
        success.setStatus(PaymentStatus.SUCCESS);
        when(stateTransitionHandlers.handleWalletCall("LUKU", request)).thenReturn(success);

        PaymentResponseDto result = retryMechanism.executePaymentWithRetry("LUKU", request);

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        verify(stateTransitionHandlers).handleWalletCall("LUKU", request);
    }

    @Test
    void shouldRetryUntilSuccess() {
        BillingRetryMechanism retryMechanism = new BillingRetryMechanism(stateTransitionHandlers, 3, 15);
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();

        PaymentResponseDto success = new PaymentResponseDto();
        success.setStatus(PaymentStatus.SUCCESS);

        when(stateTransitionHandlers.handleWalletCall("LUKU", request))
                .thenThrow(new IllegalStateException("timeout"))
                .thenThrow(new IllegalStateException("timeout"))
                .thenReturn(success);

        PaymentResponseDto result = retryMechanism.executePaymentWithRetry("LUKU", request);

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        verify(stateTransitionHandlers, times(3)).handleWalletCall("LUKU", request);
    }

    @Test
    void shouldOpenCircuitAfterConsecutiveFailures() {
        BillingRetryMechanism retryMechanism = new BillingRetryMechanism(stateTransitionHandlers, 1, 60);
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();

        when(stateTransitionHandlers.handleWalletCall(eq("LUKU"), any()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThrows(IllegalStateException.class, () -> retryMechanism.executePaymentWithRetry("LUKU", request));

        PaymentResponseDto blocked = retryMechanism.executePaymentWithRetry("LUKU", request);
        assertNotNull(blocked);
        assertEquals(PaymentStatus.FAILED, blocked.getStatus());
        assertEquals("Circuit open for billing retry mechanism", blocked.getMessage());

        verify(stateTransitionHandlers, times(1)).handleWalletCall("LUKU", request);
    }
}
