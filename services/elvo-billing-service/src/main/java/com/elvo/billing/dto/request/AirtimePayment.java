package com.elvo.billing.dto.request;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.AssertTrue;

public class AirtimePayment extends UtilityPaymentRequestDto {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AirtimePayment() {
        setLookupRequired(false);
    }

    public void setRecipientMobileNumber(String mobileNumber) {
        setReferenceNumber(mobileNumber);
    }

    public void setNetworkCode(String networkCode) {
        putMetadataValue("networkCode", networkCode);
    }

    public void setRecipientName(String recipientName) {
        putMetadataValue("recipientName", recipientName);
    }

    @AssertTrue(message = "airtime referenceNumber must be a mobile number")
    public boolean isAirtimeReferenceValid() {
        String reference = getReferenceNumber();
        return reference != null && reference.matches("^[0-9]{10,15}$");
    }

    @AssertTrue(message = "airtime metadata requires networkCode and recipientName")
    public boolean isAirtimeMetadataValid() {
        Map<String, Object> metadata = readMetadata();
        return hasText(metadata.get("networkCode")) && hasText(metadata.get("recipientName"));
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