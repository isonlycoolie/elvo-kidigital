package com.elvo.billing.security;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.elvo.billing.entity.enums.PaymentStatus;

@Component
public class BillingPaymentStateTransitionValidator {

    private static final EnumSet<PaymentStatus> COMPLETE_ALLOWED_FROM = EnumSet.of(PaymentStatus.PROCESSING);
    private static final EnumSet<PaymentStatus> REVERSE_ALLOWED_FROM = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (to) {
            case SUCCESS -> COMPLETE_ALLOWED_FROM.contains(from);
            case REVERSED -> REVERSE_ALLOWED_FROM.contains(from);
            default -> false;
        };
    }
}
