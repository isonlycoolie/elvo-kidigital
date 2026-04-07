package com.elvo.billing.entity.enums;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentStatusTest {

    @Test
    void shouldExposeExpectedPaymentStatusesInDefinedOrder() {
        List<String> names = List.of(PaymentStatus.values()).stream().map(Enum::name).toList();

        assertEquals(List.of(
            "INITIATED",
            "PENDING",
            "PROCESSING",
            "SUCCESS",
            "FAILED",
            "REVERSED"
        ), names);
    }

    @Test
    void shouldParseKnownStatusByName() {
        assertEquals(PaymentStatus.PROCESSING, PaymentStatus.valueOf("PROCESSING"));
    }
}