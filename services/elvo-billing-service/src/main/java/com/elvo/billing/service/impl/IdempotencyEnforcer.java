package com.elvo.billing.service.impl;

import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.IdempotencyKey;
import com.elvo.billing.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency enforcement service.
 * Checks for duplicate operations and returns cached results when applicable.
 */
@Component
public class IdempotencyEnforcer {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public IdempotencyEnforcer(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    /**
     * Check if an idempotency key has been processed.
     * If found, return the cached result; otherwise, return empty.
     * 
     * @param idempotencyKey the idempotency key
     * @return Optional containing the cached PaymentResponseDto if the key exists
     */
    public Optional<PaymentResponseDto> getCachedResult(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByKey(idempotencyKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        try {
            PaymentResponseDto cached = objectMapper.readValue(existing.get().getResultPayload(), PaymentResponseDto.class);
            return Optional.of(cached);
        } catch (Exception ex) {
            // If deserialization fails, treat it as a cache miss
            return Optional.empty();
        }
    }

    /**
     * Record a successful operation result for an idempotency key.
     * 
     * @param idempotencyKey the idempotency key
     * @param operationId the ID of the operation (e.g., paymentId)
     * @param operationType the type of operation (e.g., "PAYMENT_CREATION")
     * @param result the result to cache
     */
    public void recordResult(String idempotencyKey, UUID operationId, String operationType, PaymentResponseDto result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        try {
            String resultPayload = objectMapper.writeValueAsString(result);
            IdempotencyKey entry = new IdempotencyKey(idempotencyKey, operationId, operationType, resultPayload);
            idempotencyKeyRepository.save(entry);
        } catch (Exception ex) {
            // Log but do not throw; idempotency recording failure should not block the operation
        }
    }
}
