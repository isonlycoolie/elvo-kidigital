package com.elvo.billing.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import org.slf4j.MDC;
import org.junit.jupiter.api.Test;

class GenericRequestMapperTest {

    private final GenericRequestMapper mapper = new GenericRequestMapper();

    @Test
    void shouldMapUtilityRequestToProviderPayload() {
        MDC.put("idempotencyKey", "idem-123");
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-100");
        request.setAmount(BigDecimal.valueOf(1200));
        request.setCustomerPhone("255700000123");
        request.setCustomerName("Test User");
        request.setLookupRequired(true);
        request.setMetadata("{\"source\":\"unit-test\",\"priority\":1}");

        Map<String, Object> providerRequest = mapper.toProviderRequest(request);

        assertThat(providerRequest).containsEntry("referenceNumber", "REF-100");
        assertThat(providerRequest).containsEntry("amount", BigDecimal.valueOf(1200));
        assertThat(providerRequest).containsEntry("customerPhone", "255700000123");
        assertThat(providerRequest).containsEntry("customerName", "Test User");
        assertThat(providerRequest).containsEntry("lookupRequired", true);
        assertThat(providerRequest.get("metadata")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) providerRequest.get("metadata");
        assertThat(metadata).containsEntry("source", "unit-test");
        assertThat(providerRequest).containsEntry("idempotencyKey", "idem-123");
        MDC.clear();
    }

    @Test
    void shouldOmitNullOptionalFieldsAndNormalizeEmptyMetadata() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-200");
        request.setLookupRequired(false);

        Map<String, Object> providerRequest = mapper.toProviderRequest(request);

        assertThat(providerRequest).doesNotContainKeys("amount", "customerPhone", "customerName");
        assertThat(providerRequest.get("metadata")).isEqualTo(Map.of());
    }

    @Test
    void shouldRejectInvalidMetadataJson() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-300");
        request.setMetadata("not-json");

        assertThatThrownBy(() -> mapper.toProviderRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata must contain valid JSON");
    }

    @Test
    void shouldIgnoreMissingIdempotencyKey() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-400");

        Map<String, Object> providerRequest = mapper.toProviderRequest(request);

        assertThat(providerRequest).doesNotContainKey("idempotencyKey");
    }
}