package com.elvo.identity.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

class ProvisioningRetryExecutorUnitTest {

    @Test
    void shouldRetryOnRetryableServerError() {
        ProvisioningRetryExecutor retryExecutor = new ProvisioningRetryExecutor(testProperties());
        AtomicInteger attempts = new AtomicInteger();

        retryExecutor.execute("wallet provisioning", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
            }
        });

        assertEquals(2, attempts.get());
    }

    @Test
    void shouldNotRetryOnClientErrors() {
        ProvisioningRetryExecutor retryExecutor = new ProvisioningRetryExecutor(testProperties());
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> retryExecutor.execute("wallet provisioning", () -> {
            attempts.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        }));

        assertEquals(1, attempts.get());
    }

    private ProvisioningClientProperties testProperties() {
        ProvisioningClientProperties properties = new ProvisioningClientProperties();
        properties.setMaxAttempts(3);
        properties.setRetryBackoffMs(1);
        properties.setRetryBackoffMultiplier(2.0);
        properties.setRetryMaxBackoffMs(5);
        return properties;
    }
}
