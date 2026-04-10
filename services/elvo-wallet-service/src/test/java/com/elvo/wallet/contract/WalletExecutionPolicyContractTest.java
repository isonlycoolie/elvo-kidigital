package com.elvo.wallet.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WalletExecutionPolicyContractTest {

    @Test
    void denyShouldBuildFailureMessageWithReasonCodeAndDetail() {
        WalletExecutionPolicyContract contract = WalletExecutionPolicyContract.deny(
                "identity-service",
                "IDENTITY_ESP_EAC_VERIFICATION_FAILED",
                "ESP/EAC verification failed",
                null,
                null,
                "EAC_FAILED");

        assertThat(contract.allowed()).isFalse();
        assertThat(contract.toFailureMessage()).isEqualTo("IDENTITY_ESP_EAC_VERIFICATION_FAILED: ESP/EAC verification failed");
    }

    @Test
    void allowShouldRemainAllowedWithNormalizedVersion() {
        WalletExecutionPolicyContract contract = WalletExecutionPolicyContract.allow(
                "account-service",
                "ACCOUNT_POLICY_ALLOW",
                "Transfer allowed by account policy",
                "ACTIVE",
                "VERIFIED",
                "NOT_EVALUATED");

        assertThat(contract.allowed()).isTrue();
        assertThat(contract.version()).isEqualTo("v1");
    }
}
