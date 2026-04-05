package com.elvo.identity.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EanGeneratorUnitTest {

    @Test
    void generatedEanShouldMatchExpectedFormatAndBeUppercase() {
        EanGenerator generator = new EanGenerator();
        String ean = generator.generate();

        assertTrue(ean.matches("^ELVO-[A-Z0-9]{12}$"));
        assertTrue(ean.equals(ean.toUpperCase()));
    }

    @Test
    void generatedEansShouldBeHighlyUniqueAcrossSampleSet() {
        EanGenerator generator = new EanGenerator();
        Set<String> values = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            values.add(generator.generate());
        }

        assertTrue(values.size() > 995);
    }
}
