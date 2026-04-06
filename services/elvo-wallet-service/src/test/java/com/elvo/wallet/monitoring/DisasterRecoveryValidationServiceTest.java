package com.elvo.wallet.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisasterRecoveryValidationServiceTest {

    @Test
    void shouldPassWhenDistinctRegionsAndLagWithinThreshold() {
        DisasterRecoveryValidationService service = new DisasterRecoveryValidationService(
                "active-passive",
                "eu-west-1",
                "eu-central-1",
                60,
                10);

        DisasterRecoveryValidationService.ValidationReport report = service.validateReadiness();

        assertThat(report.ready()).isTrue();
        assertThat(report.checks()).containsEntry("regions_are_distinct", true);
    }

    @Test
    void shouldFailWhenRegionsAreSame() {
        DisasterRecoveryValidationService service = new DisasterRecoveryValidationService(
                "active-passive",
                "eu-west-1",
                "eu-west-1",
                60,
                10);

        DisasterRecoveryValidationService.ValidationReport report = service.validateReadiness();

        assertThat(report.ready()).isFalse();
        assertThat(report.checks()).containsEntry("regions_are_distinct", false);
    }

    @Test
    void shouldFailWhenReplicationLagExceedsThreshold() {
        DisasterRecoveryValidationService service = new DisasterRecoveryValidationService(
                "active-active",
                "eu-west-1",
                "eu-central-1",
                30,
                45);

        DisasterRecoveryValidationService.ValidationReport report = service.validateReadiness();

        assertThat(report.ready()).isFalse();
        assertThat(report.checks()).containsEntry("replication_lag_within_threshold", false);
    }
}
