package com.elvo.billing.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingServiceAuthorizationMatrixTest {

    private final BillingServiceAuthorizationMatrix matrix = new BillingServiceAuthorizationMatrix(
            new BillingServiceAuthorizationProperties());

    @Test
    void shouldAllowBillingServiceToPublishConfiguredEvents() {
        assertThat(matrix.isAllowed("billing-service", "PUBLISH", "billing.transaction.requested")).isTrue();
        assertThat(matrix.isAllowed("billing-service", "PUBLISH", "billing.payment.reversed")).isTrue();
    }

    @Test
    void shouldRejectBillingServiceForUnconfiguredEvent() {
        assertThat(matrix.isAllowed("billing-service", "PUBLISH", "wallet.transaction.completed.queue")).isFalse();
    }

    @Test
    void shouldAllowWalletServiceToConsumeConfiguredQueue() {
        assertThat(matrix.isAllowed("wallet-service", "CONSUME", "wallet.transaction.completed.queue")).isTrue();
    }

    @Test
    void shouldRejectUnknownService() {
        assertThat(matrix.isAllowed("unknown-service", "CONSUME", "wallet.transaction.completed.queue")).isFalse();
    }
}
