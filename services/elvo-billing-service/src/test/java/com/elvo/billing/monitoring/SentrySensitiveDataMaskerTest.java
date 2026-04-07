package com.elvo.billing.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentrySensitiveDataMaskerTest {

    private final SentrySensitiveDataMasker masker = new SentrySensitiveDataMasker();

    @Test
    void shouldMaskReference() {
        assertThat(masker.maskReference("REF-123456789")).isEqualTo("****6789");
    }

    @Test
    void shouldMaskPhone() {
        assertThat(masker.maskPhone("+255700123456")).isEqualTo("***456");
    }

    @Test
    void shouldMaskNumericValuesInText() {
        String masked = masker.maskText("payment failed for +255700123456 amount 1200.50");
        assertThat(masked).contains("***456");
        assertThat(masked).doesNotContain("1200.50");
    }
}
