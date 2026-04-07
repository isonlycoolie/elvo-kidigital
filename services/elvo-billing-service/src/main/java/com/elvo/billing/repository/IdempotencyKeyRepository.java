package com.elvo.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.billing.entity.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}
