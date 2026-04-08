package com.elvo.billing.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    void maskIdentifierShouldPreserveOnlyPrefixAndSuffix() {
        assertThat(SensitiveDataMasker.maskIdentifier("REQ-123456789")).isEqualTo("RE****89");
        assertThat(SensitiveDataMasker.maskIdentifier("1234")).isEqualTo("****");
    }

    @Test
    void maskTextShouldRedactSensitiveKeyValues() {
        String raw = "token=abc123 reference=REF-00001 metadata={\"secret\":\"xyz\"}";
        String masked = SensitiveDataMasker.maskText(raw);

        assertThat(masked).contains("token=***");
        assertThat(masked).contains("reference=***");
        assertThat(masked).contains("metadata=***");
        assertThat(masked).doesNotContain("abc123");
    }
}
