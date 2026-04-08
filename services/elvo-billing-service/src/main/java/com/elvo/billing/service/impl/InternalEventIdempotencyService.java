package com.elvo.billing.service.impl;

import com.elvo.billing.entity.ProcessedInternalEvent;
import com.elvo.billing.repository.ProcessedInternalEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class InternalEventIdempotencyService {

    private final ProcessedInternalEventRepository processedInternalEventRepository;

    public InternalEventIdempotencyService(ProcessedInternalEventRepository processedInternalEventRepository) {
        this.processedInternalEventRepository = processedInternalEventRepository;
    }

    public boolean markIfFirstProcessed(String eventId,
                                        String idempotencyKey,
                                        String eventType,
                                        String sourceService) {
        if (isBlank(eventId) || isBlank(idempotencyKey) || isBlank(eventType) || isBlank(sourceService)) {
            return false;
        }

        ProcessedInternalEvent record = new ProcessedInternalEvent();
        record.setEventId(eventId.trim());
        record.setIdempotencyKey(idempotencyKey.trim());
        record.setEventType(eventType.trim());
        record.setSourceService(sourceService.trim());

        try {
            processedInternalEventRepository.save(record);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
