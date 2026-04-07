package com.elvo.billing.statemachine;

import com.elvo.billing.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingTransactionStateMachineTest {

    private final BillingTransactionStateMachine stateMachine = new BillingTransactionStateMachine();

    @Test
    void shouldAllowHappyPathTransitions() {
        PaymentStatus pending = stateMachine.transition(PaymentStatus.INITIATED, PaymentStatus.PENDING);
        PaymentStatus processing = stateMachine.transition(pending, PaymentStatus.PROCESSING);
        PaymentStatus success = stateMachine.transition(processing, PaymentStatus.SUCCESS);

        assertEquals(PaymentStatus.SUCCESS, success);
    }

    @Test
    void shouldAllowFailureAndReversalTransitions() {
        PaymentStatus failed = stateMachine.transition(PaymentStatus.PROCESSING, PaymentStatus.FAILED);
        PaymentStatus reversed = stateMachine.transition(failed, PaymentStatus.REVERSED);

        assertEquals(PaymentStatus.REVERSED, reversed);
        assertTrue(stateMachine.canTransition(PaymentStatus.SUCCESS, PaymentStatus.REVERSED));
    }

    @Test
    void shouldRejectInvalidTransition() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> stateMachine.transition(PaymentStatus.INITIATED, PaymentStatus.PROCESSING));

        assertEquals("invalid billing transition from INITIATED to PROCESSING", ex.getMessage());
    }

    @Test
    void shouldReturnFalseForNullAndSameStatus() {
        assertFalse(stateMachine.canTransition(null, PaymentStatus.PENDING));
        assertFalse(stateMachine.canTransition(PaymentStatus.PENDING, null));
        assertFalse(stateMachine.canTransition(PaymentStatus.PENDING, PaymentStatus.PENDING));
    }
}
