package com.elvo.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AmlCaseWorkflowServiceTest {

    @Test
    void shouldCreateAndResolveAmlCase() {
        AmlCaseWorkflowService service = new AmlCaseWorkflowService();

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        AmlCaseWorkflowService.AmlCase created = service.createCase(
                "FRAUD_RULE_BLOCK",
                userId,
                walletId,
                "withdrawal",
                "Velocity risk detected",
                Map.of("amount", "500.00"));

        assertThat(created.caseId()).isNotBlank();
        assertThat(created.status()).isEqualTo(AmlCaseWorkflowService.CaseStatus.OPEN);

        AmlCaseWorkflowService.AmlCase underReview = service.setUnderReview(created.caseId());
        assertThat(underReview).isNotNull();
        assertThat(underReview.status()).isEqualTo(AmlCaseWorkflowService.CaseStatus.UNDER_REVIEW);

        AmlCaseWorkflowService.AmlCase resolved = service.resolveCase(
                created.caseId(),
                true,
                "Escalated and reported to compliance team",
                "fraud-admin");
        assertThat(resolved).isNotNull();
        assertThat(resolved.status()).isEqualTo(AmlCaseWorkflowService.CaseStatus.RESOLVED);
        assertThat(resolved.suspiciousActivityConfirmed()).isTrue();

        assertThat(service.listCases(AmlCaseWorkflowService.CaseStatus.RESOLVED, 10)).hasSize(1);
    }
}
