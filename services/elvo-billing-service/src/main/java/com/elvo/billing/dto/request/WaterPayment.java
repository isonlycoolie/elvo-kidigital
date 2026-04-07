package com.elvo.billing.dto.request;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.AssertTrue;

public class WaterPayment extends UtilityPaymentRequestDto {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public WaterPayment() {
        setLookupRequired(true);
    }

    public void setInstitution(String institution) {
        putMetadataValue("institution", institution);
    }

    public void setBillingPeriod(String billingPeriod) {
        putMetadataValue("billingPeriod", billingPeriod);
    }

    @AssertTrue(message = "water metadata requires institution and billingPeriod")
    public boolean isWaterMetadataValid() {
        Map<String, Object> metadata = readMetadata();
        return hasText(metadata.get("institution")) && hasText(metadata.get("billingPeriod"));
    }

    private void putMetadataValue(String key, String value) {
        Map<String, Object> metadata = new LinkedHashMap<>(readMetadata());
        metadata.put(key, value);
        setMetadata(writeMetadata(metadata));
    }

    private Map<String, Object> readMetadata() {
        try {
            return objectMapper.readValue(getMetadata(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }
}