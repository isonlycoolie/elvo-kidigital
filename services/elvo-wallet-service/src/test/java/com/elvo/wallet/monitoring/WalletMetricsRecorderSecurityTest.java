package com.elvo.wallet.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class WalletMetricsRecorderSecurityTest {

    @Test
    void shouldRecordSecurityAndAmlMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        WalletMetricsRecorder recorder = new WalletMetricsRecorder(meterRegistry, "wallet-service");

        recorder.recordSecurityControl("sanctions", true);
        recorder.recordSecurityControl("sanctions", false);
        recorder.recordAmlCase("opened", "sanctions_or_blacklist");
        recorder.recordAmlCase("resolved", "fraud_rule_block");

        double blocked = meterRegistry.find("wallet_security_controls_total")
                .tag("control", "sanctions")
                .tag("action", "blocked")
                .counter()
                .count();
        double opened = meterRegistry.find("wallet_aml_cases_total")
                .tag("status", "opened")
                .counter()
                .count();

        assertThat(blocked).isEqualTo(1.0);
        assertThat(opened).isEqualTo(1.0);
    }
}
