package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

class SerializableTransactionRetryExecutorTest {

    @Test
    void shouldRetryTransientSerializableConflict() {
        SerializableTransactionRetryExecutor executor = new SerializableTransactionRetryExecutor(3, 1);
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotAcquireLockException("could not serialize access due to concurrent update");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldFailWhenRetryLimitIsExceeded() {
        SerializableTransactionRetryExecutor executor = new SerializableTransactionRetryExecutor(2, 1);

        assertThatThrownBy(() -> executor.execute(() -> {
            throw new CannotAcquireLockException("serialization failure");
        })).isInstanceOf(CannotAcquireLockException.class);
    }
}
