package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.elvo.wallet.service.EacReplayProtectionService;

class InMemoryEacReplayProtectionServiceTest {

    @Test
    void shouldAcceptFirstUseAndRejectReplayWithinWindow() {
        InMemoryEacReplayProtectionService service = new InMemoryEacReplayProtectionService(300);
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        EacReplayProtectionService.EacValidationResult first =
                service.validateAndConsume(userId, "eac-123", "wallet:user:idem-1");
        EacReplayProtectionService.EacValidationResult second =
                service.validateAndConsume(userId, "eac-123", "wallet:user:idem-1");

        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isFalse();
        assertThat(second.message()).isEqualTo("EAC replay detected");
    }

    @Test
    void shouldRejectBindingMismatchOnReuse() {
        InMemoryEacReplayProtectionService service = new InMemoryEacReplayProtectionService(300);
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        EacReplayProtectionService.EacValidationResult first =
                service.validateAndConsume(userId, "eac-456", "wallet:user:idem-1");
        EacReplayProtectionService.EacValidationResult second =
                service.validateAndConsume(userId, "eac-456", "wallet:user:idem-2");

        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isFalse();
        assertThat(second.message()).isEqualTo("EAC request binding mismatch");
    }
}
