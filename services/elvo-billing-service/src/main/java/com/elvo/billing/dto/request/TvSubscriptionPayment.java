package com.elvo.billing.dto.request;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.AssertTrue;

public class TvSubscriptionPayment extends UtilityPaymentRequestDto {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public TvSubscriptionPayment() {
        setLookupRequired(false);
    }

    public void setPackageCode(String packageCode) {
        putMetadataValue("packageCode", packageCode);
    }

    public void setPackageName(String packageName) {
        putMetadataValue("packageName", packageName);
    }

    public void setSubscriptionPeriod(String subscriptionPeriod) {
        putMetadataValue("subscriptionPeriod", subscriptionPeriod);
    }

    @AssertTrue(message = "tv subscription metadata requires packageCode, packageName, and subscriptionPeriod")
    public boolean isTvSubscriptionMetadataValid() {
        Map<String, Object> metadata = readMetadata();
        return hasText(metadata.get("packageCode"))
                && hasText(metadata.get("packageName"))
                && hasText(metadata.get("subscriptionPeriod"));
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