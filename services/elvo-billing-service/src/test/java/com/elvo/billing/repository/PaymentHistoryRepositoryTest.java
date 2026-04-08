package com.elvo.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.PaymentHistory;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.PaymentStatus;

@SpringBootTest
@ActiveProfiles("test")
class PaymentHistoryRepositoryTest {

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void logPaymentEventShouldPersistHistoryRecord() {
        BillPayment payment = new BillPayment();
        payment.setRequestId("req-history-001");
        payment.setCorrelationId("corr-history-001");
        payment.setIdempotencyKey("idem-history-001");
        payment.setUserId(UUID.randomUUID());
        payment.setWalletId(UUID.randomUUID());
        payment.setBillCategory(BillCategory.AIRTIME);
        payment.setServiceCode("airtimeco");
        payment.setReferenceNumber("REF-H-001");
        payment.setAmount(new BigDecimal("20.00"));
        payment.setCurrency("TZS");
        payment.setMetadata("{\"category\":\"airtime\", \"attempt\":1}");

        BillPayment createdPayment = billPaymentRepository.createPayment(payment);

        PaymentHistory history = new PaymentHistory();
        history.setPaymentId(createdPayment.getPaymentId());
        history.setRequestId(createdPayment.getRequestId());
        history.setCorrelationId(createdPayment.getCorrelationId());
        history.setEventType("PAYMENT_STATUS_CHANGED");
        history.setFromStatus(PaymentStatus.INITIATED.name());
        history.setToStatus(PaymentStatus.SUCCESS.name());
        history.setAdapterName("selcom");
        history.setAdapterReference("EXT-H-001");
        history.setResponseCode("00");
        history.setResponseMessage("Approved");
        history.setMetadata("{\"provider\":\"selcom\", \"retry\":1}");

        PaymentHistory persisted = paymentHistoryRepository.logPaymentEvent(history);

        assertThat(persisted.getHistoryId()).isNotNull();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getMetadata()).isEqualTo("{\"provider\":\"selcom\",\"retry\":1}");
        assertThat(jdbcTemplate.queryForObject(
            "select metadata from payment_history where history_id = ?",
            String.class,
            persisted.getHistoryId()))
            .startsWith("enc:v1:");

        assertThat(paymentHistoryRepository.findById(persisted.getHistoryId()))
                .isPresent()
                .get()
                .extracting(PaymentHistory::getPaymentId, PaymentHistory::getEventType, PaymentHistory::getToStatus)
                .containsExactly(createdPayment.getPaymentId(), "PAYMENT_STATUS_CHANGED", PaymentStatus.SUCCESS.name());
    }
}