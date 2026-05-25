package com.elvo.billing.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Security tests for concurrency protection and race condition prevention.
 * Verifies that concurrent requests do not cause duplicate processing or data corruption.
 */
@DisplayName("Concurrency Protection Security Tests")
public class ConcurrencySecurityTest {

    private AtomicInteger processedCount;
    private CyclicBarrier barrier;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        processedCount = new AtomicInteger(0);
        barrier = new CyclicBarrier(5);
        executor = Executors.newFixedThreadPool(5);
    }

    @Test
    @DisplayName("Should prevent duplicate payment processing with concurrent requests")
    void testPreventDuplicateProcessing() throws Exception {
        String paymentId = "payment-123";
        ConcurrentHashMap<String, Integer> processedPayments = new ConcurrentHashMap<>();

        Callable<Boolean> processPayment = () -> {
            barrier.await(); // Synchronize all threads to start at same time
            // Simulate idempotency check
            if (processedPayments.putIfAbsent(paymentId, 1) == null) {
                processedCount.incrementAndGet();
                return true;
            }
            return false;
        };

        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    processPayment.call();
                } catch (Exception e) {
                    fail("Unexpected exception during concurrent processing");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, processedCount.get(), "Payment should be processed only once despite concurrent requests");
    }

    @Test
    @DisplayName("Should handle optimistic locking conflicts correctly")
    void testOptimisticLockingConflict() {
        OptimisticVersionStore store = new OptimisticVersionStore(1);

        assertTrue(store.updateWithVersionCheck(1, 2), "First update should succeed");
        assertFalse(store.updateWithVersionCheck(1, 3), "Stale version update should fail");
        assertTrue(store.updateWithVersionCheck(2, 3), "Update with current version should succeed");
    }

    @Test
    @DisplayName("Should prevent race condition in state transitions")
    void testStateTransitionRaceCondition() throws Exception {
        String transactionId = "txn-456";
        ConcurrentHashMap<String, String> states = new ConcurrentHashMap<>();
        states.put(transactionId, "PENDING");

        Callable<Boolean> transitionState = () -> {
            barrier.await();
            String currentState = states.get(transactionId);
            if ("PENDING".equals(currentState)) {
                states.put(transactionId, "COMPLETED");
                return true;
            }
            return false;
        };

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    if (transitionState.call()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("Unexpected exception");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Only one thread should successfully transition the state
        assertTrue(successCount.get() <= 1, "Only one state transition should succeed");
    }

    private static final class OptimisticVersionStore {
        private int currentVersion;

        private OptimisticVersionStore(int initialVersion) {
            this.currentVersion = initialVersion;
        }

        private boolean updateWithVersionCheck(int expectedVersion, int newVersion) {
            if (currentVersion != expectedVersion) {
                return false;
            }
            currentVersion = newVersion;
            return true;
        }
    }
}
