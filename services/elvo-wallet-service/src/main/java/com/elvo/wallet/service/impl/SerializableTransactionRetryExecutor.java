package com.elvo.wallet.service.impl;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Component;

@Component
public class SerializableTransactionRetryExecutor {

    private final int maxAttempts;
    private final Duration baseBackoff;

    public SerializableTransactionRetryExecutor(
            @Value("${elvo.security.serializable-retry.max-attempts:4}") int maxAttempts,
            @Value("${elvo.security.serializable-retry.base-backoff-ms:100}") long baseBackoffMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoff = Duration.ofMillis(Math.max(1L, baseBackoffMs));
    }

    public <T> T execute(Supplier<T> operation) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException ex) {
                if (!isRetryable(ex) || attempt >= maxAttempts) {
                    throw ex;
                }
                lastError = ex;
                pause(backoffForAttempt(attempt));
            }
        }
        throw lastError == null ? new IllegalStateException("Serializable retry executor failed") : lastError;
    }

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof CannotAcquireLockException
                || exception instanceof DeadlockLoserDataAccessException
                || exception instanceof PessimisticLockingFailureException
                || exception instanceof TransientDataAccessException) {
            return true;
        }

        if (exception instanceof JpaSystemException jpaSystemException
                && jpaSystemException.getMostSpecificCause() != null) {
            String message = jpaSystemException.getMostSpecificCause().getMessage();
            return containsSerializableSignal(message);
        }

        return containsSerializableSignal(exception.getMessage());
    }

    private boolean containsSerializableSignal(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("could not serialize access")
                || normalized.contains("serialization failure")
                || normalized.contains("deadlock detected")
                || normalized.contains("retry transaction");
    }

    private Duration backoffForAttempt(int attempt) {
        long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 6);
        return baseBackoff.multipliedBy(multiplier);
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying serializable transaction", interruptedException);
        }
    }
}
