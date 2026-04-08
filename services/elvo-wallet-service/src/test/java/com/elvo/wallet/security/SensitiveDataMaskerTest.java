package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    void maskIdentifierShouldPreserveOnlyPrefixAndSuffix() {
        assertThat(SensitiveDataMasker.maskIdentifier("USER-99887766")).isEqualTo("US****66");
        assertThat(SensitiveDataMasker.maskIdentifier("abc")).isEqualTo("****");
    }

    @Test
    void maskTextShouldRedactSensitiveKeyValues() {
        String raw = "phone=255700000001 token=jwt-abc secret=top";
        String masked = SensitiveDataMasker.maskText(raw);

        assertThat(masked).contains("phone=***");
        assertThat(masked).contains("token=***");
        assertThat(masked).contains("secret=***");
        assertThat(masked).doesNotContain("jwt-abc");
    }
}
