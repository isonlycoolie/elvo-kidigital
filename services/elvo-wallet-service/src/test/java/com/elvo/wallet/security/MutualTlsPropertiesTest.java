package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MutualTlsPropertiesTest {

    @Test
    void shouldDefaultToDisabledInLocalProfiles() {
        MutualTlsProperties properties = new MutualTlsProperties();

        assertThat(properties.isEnabledForProfiles(new String[] {"dev"})).isFalse();
        assertThat(properties.isEnabledForProfiles(new String[] {"local"})).isFalse();
        assertThat(properties.isEnabledForProfiles(new String[] {"test"})).isFalse();
    }

    @Test
    void shouldDefaultToEnabledInNonLocalProfiles() {
        MutualTlsProperties properties = new MutualTlsProperties();

        assertThat(properties.isEnabledForProfiles(new String[] {"prod"})).isTrue();
        assertThat(properties.isEnabledForProfiles(new String[] {"staging"})).isTrue();
    }

    @Test
    void explicitSettingShouldOverrideProfileDefault() {
        MutualTlsProperties properties = new MutualTlsProperties();
        properties.setEnabled(Boolean.FALSE);

        assertThat(properties.isEnabledForProfiles(new String[] {"prod"})).isFalse();

        properties.setEnabled(Boolean.TRUE);
        assertThat(properties.isEnabledForProfiles(new String[] {"dev"})).isTrue();
    }
}
