package com.elvo.billing.entity.enums;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LookupStatusTest {

    @Test
    void shouldExposeExpectedLookupStatusesInDefinedOrder() {
        List<String> names = List.of(LookupStatus.values()).stream().map(Enum::name).toList();

        assertEquals(List.of(
            "SUCCESS",
            "FAILED",
            "NOT_FOUND"
        ), names);
    }

    @Test
    void shouldParseKnownStatusByName() {
        assertEquals(LookupStatus.NOT_FOUND, LookupStatus.valueOf("NOT_FOUND"));
    }
}