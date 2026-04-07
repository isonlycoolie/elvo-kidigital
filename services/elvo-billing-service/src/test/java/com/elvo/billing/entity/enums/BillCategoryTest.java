package com.elvo.billing.entity.enums;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillCategoryTest {

    @Test
    void shouldExposeExpectedBillCategoriesInDefinedOrder() {
        List<String> names = List.of(BillCategory.values()).stream().map(Enum::name).toList();

        assertEquals(List.of(
            "GOVERNMENT",
            "ELECTRICITY",
            "WATER",
            "TV_SUBSCRIPTION",
            "AIRTIME",
            "INTERNET",
            "HOSPITAL",
            "AIRLINE"
        ), names);
    }

    @Test
    void shouldParseKnownCategoryByName() {
        assertEquals(BillCategory.TV_SUBSCRIPTION, BillCategory.valueOf("TV_SUBSCRIPTION"));
    }
}