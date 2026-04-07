package com.elvo.billing.dto.request;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.AssertTrue;

public class AirlinePayment extends UtilityPaymentRequestDto {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AirlinePayment() {
        setLookupRequired(true);
    }

    public void setBookingCode(String bookingCode) {
        putMetadataValue("bookingCode", bookingCode);
    }

    public void setPassengerName(String passengerName) {
        putMetadataValue("passengerName", passengerName);
    }

    public void setTravelDate(String travelDate) {
        putMetadataValue("travelDate", travelDate);
    }

    @AssertTrue(message = "airline metadata requires bookingCode, passengerName, and travelDate")
    public boolean isAirlineMetadataValid() {
        Map<String, Object> metadata = readMetadata();
        return hasText(metadata.get("bookingCode"))
                && hasText(metadata.get("passengerName"))
                && hasText(metadata.get("travelDate"));
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