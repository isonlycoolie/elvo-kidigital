package com.elvo.billing.repository;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BillingMetadataJsonNormalizer {

    private final ObjectMapper objectMapper;

    public BillingMetadataJsonNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalize(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return "{}";
        }

        try {
            JsonNode parsed = objectMapper.readTree(metadata);
            return objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("metadata must contain valid JSON", ex);
        }
    }
}