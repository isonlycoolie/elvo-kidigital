package com.elvo.billing.statemachine;

import com.elvo.billing.entity.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class BillingTransactionStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PaymentStatus.INITIATED, EnumSet.of(PaymentStatus.PENDING));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.PROCESSING));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PROCESSING, EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.SUCCESS, EnumSet.of(PaymentStatus.REVERSED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.of(PaymentStatus.REVERSED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.REVERSED, EnumSet.noneOf(PaymentStatus.class));
    }

    public PaymentStatus transition(PaymentStatus currentStatus, PaymentStatus nextStatus) {
        if (!canTransition(currentStatus, nextStatus)) {
            throw new IllegalStateException("invalid billing transition from " + currentStatus + " to " + nextStatus);
        }
        return nextStatus;
    }

    public boolean canTransition(PaymentStatus currentStatus, PaymentStatus nextStatus) {
        if (currentStatus == null || nextStatus == null || currentStatus == nextStatus) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(nextStatus);
    }
}
