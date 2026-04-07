package com.elvo.billing.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.elvo.billing.entity.IdempotencyKey;
import com.elvo.billing.exception.DuplicatePaymentException;
import com.elvo.billing.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyEnforcerTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Test
    void shouldAllowFirstTimeIdempotentRequest() {
        IdempotencyEnforcer enforcer = new IdempotencyEnforcer(idempotencyKeyRepository);
        when(idempotencyKeyRepository.findById("IDEMP-1")).thenReturn(Optional.empty());

        enforcer.assertNotProcessed("IDEMP-1", "PAYMENT_EXECUTE", "hash-1");
        enforcer.markProcessed("IDEMP-1", "PAYMENT_EXECUTE", "hash-1", "{}");

        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    @Test
    void shouldRejectDuplicateRequestWithDifferentPayload() {
        IdempotencyEnforcer enforcer = new IdempotencyEnforcer(idempotencyKeyRepository);
        IdempotencyKey existing = new IdempotencyKey();
        existing.setIdempotencyKey("IDEMP-1");
        existing.setOperation("PAYMENT_EXECUTE");
        existing.setRequestHash("hash-1");

        when(idempotencyKeyRepository.findById("IDEMP-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> enforcer.assertNotProcessed("IDEMP-1", "PAYMENT_EXECUTE", "hash-2"))
                .isInstanceOf(DuplicatePaymentException.class)
                .hasMessageContaining("duplicate idempotencyKey detected");
    }
}
