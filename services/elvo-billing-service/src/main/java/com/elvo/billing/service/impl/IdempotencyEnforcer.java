package com.elvo.billing.service.impl;

import java.util.Objects;

import com.elvo.billing.entity.IdempotencyKey;
import com.elvo.billing.exception.DuplicatePaymentException;
import com.elvo.billing.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyEnforcer {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyEnforcer(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    public void assertNotProcessed(String idempotencyKey, String operation, String requestHash) {
        IdempotencyKey existing = idempotencyKeyRepository.findById(idempotencyKey).orElse(null);
        if (existing == null) {
            return;
        }

        if (!Objects.equals(existing.getOperation(), operation) || !Objects.equals(existing.getRequestHash(), requestHash)) {
            throw new DuplicatePaymentException("duplicate idempotencyKey detected");
        }

        throw new DuplicatePaymentException("idempotent request already processed");
    }

    public void markProcessed(String idempotencyKey, String operation, String requestHash, String responsePayload) {
        IdempotencyKey record = new IdempotencyKey();
        record.setIdempotencyKey(idempotencyKey);
        record.setOperation(operation);
        record.setRequestHash(requestHash);
        record.setResponsePayload(responsePayload);
        idempotencyKeyRepository.save(record);
    }
}
