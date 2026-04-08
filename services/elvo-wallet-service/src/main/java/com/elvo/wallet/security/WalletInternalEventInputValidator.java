package com.elvo.wallet.security;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class WalletInternalEventInputValidator {

    private static final Set<String> REQUIRED_ROOT_FIELDS = Set.of(
            "eventType",
            "requestId",
            InternalServiceMessageAuthenticator.MESSAGE_ID_FIELD,
            InternalServiceMessageAuthenticator.NONCE_FIELD,
            InternalServiceMessageAuthenticator.EXPIRES_AT_FIELD,
            "occurredAt",
            InternalServiceMessageAuthenticator.SOURCE_SERVICE_FIELD,
            InternalServiceMessageAuthenticator.SERVICE_TOKEN_FIELD,
            "payload");

    private static final Set<String> ALLOWED_PAYLOAD_FIELDS = Set.of("reservationId", "transactionId", "idempotencyKey", "reason");

    public boolean isValidBillingCompletedEvent(Map<String, Object> event) {
        return isValid(event, "billing.transaction.completed");
    }

    public boolean isValidBillingReversedEvent(Map<String, Object> event) {
        return isValid(event, "billing.transaction.reversed");
    }

    @SuppressWarnings("unchecked")
    private boolean isValid(Map<String, Object> event, String expectedEventType) {
        if (event == null) {
            return false;
        }

        for (String required : REQUIRED_ROOT_FIELDS) {
            if (isBlank(event.get(required))) {
                return false;
            }
        }

        String eventType = String.valueOf(event.get("eventType")).trim();
        if (!expectedEventType.equals(eventType)) {
            return false;
        }

        if (!isIsoInstant(String.valueOf(event.get("occurredAt")).trim())) {
            return false;
        }
        if (!isIsoInstant(String.valueOf(event.get(InternalServiceMessageAuthenticator.EXPIRES_AT_FIELD)).trim())) {
            return false;
        }

        Object payloadObject = event.get("payload");
        if (!(payloadObject instanceof Map<?, ?> rawPayload)) {
            return false;
        }
        Map<String, Object> payload = (Map<String, Object>) rawPayload;
        if (!ALLOWED_PAYLOAD_FIELDS.containsAll(payload.keySet())) {
            return false;
        }

        String reservationId = textValue(payload.get("reservationId"));
        if (reservationId == null) {
            reservationId = textValue(payload.get("transactionId"));
        }
        if (reservationId == null || !isUuid(reservationId)) {
            return false;
        }

        String idempotencyKey = textValue(payload.get("idempotencyKey"));
        if (idempotencyKey == null) {
            idempotencyKey = textValue(payload.get("transactionId"));
        }
        return idempotencyKey != null;
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isIsoInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
