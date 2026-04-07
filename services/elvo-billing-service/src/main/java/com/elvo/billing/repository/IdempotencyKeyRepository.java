package com.elvo.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.billing.entity.IdempotencyKey;

/**
 * Repository for idempotency key tracking.
 * Prevents duplicate execution of operations with the same idempotency key.
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    /**
     * Find an existing idempotency key.
     * 
     * @param key the idempotency key
     * @return Optional containing the IdempotencyKey if found
     */
    Optional<IdempotencyKey> findByKey(String key);
}
