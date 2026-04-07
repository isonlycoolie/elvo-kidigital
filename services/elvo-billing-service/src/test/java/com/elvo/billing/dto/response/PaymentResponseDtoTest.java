package com.elvo.billing.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.elvo.billing.entity.enums.PaymentStatus;

class PaymentResponseDtoTest {

    @Test
    void defaultsShouldMatchContract() {
        PaymentResponseDto dto = new PaymentResponseDto();

        assertThat(dto.getMetadata()).isEqualTo("{}");
    }

    @Test
    void settersShouldPopulateFields() {
        PaymentResponseDto dto = new PaymentResponseDto();
        Instant completedAt = Instant.now();
        UUID paymentId = UUID.randomUUID();

        dto.setPaymentId(paymentId);
        dto.setExternalReference("EXT-RESP-001");
        dto.setStatus(PaymentStatus.SUCCESS);
        dto.setMessage("Payment completed");
        dto.setReceiptNumber("RCP-001");
        dto.setPaidAmount(new BigDecimal("99.99"));
        dto.setCurrency("TZS");
        dto.setCompletedAt(completedAt);
        dto.setMetadata("{\"provider\":\"selcom\"}");

        assertThat(dto.getPaymentId()).isEqualTo(paymentId);
        assertThat(dto.getExternalReference()).isEqualTo("EXT-RESP-001");
        assertThat(dto.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(dto.getMessage()).isEqualTo("Payment completed");
        assertThat(dto.getReceiptNumber()).isEqualTo("RCP-001");
        assertThat(dto.getPaidAmount()).isEqualTo(new BigDecimal("99.99"));
        assertThat(dto.getCurrency()).isEqualTo("TZS");
        assertThat(dto.getCompletedAt()).isEqualTo(completedAt);
        assertThat(dto.getMetadata()).isEqualTo("{\"provider\":\"selcom\"}");
    }
}