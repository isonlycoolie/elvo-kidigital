package com.elvo.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.PaymentStatus;

@SpringBootTest
@ActiveProfiles("test")
class BillPaymentRepositoryTest {

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Test
    void createAndLookupPaymentShouldPersistAndReturnRecord() {
        BillPayment payment = new BillPayment();
        payment.setRequestId("req-123");
        payment.setCorrelationId("corr-123");
        payment.setIdempotencyKey("idem-123");
        payment.setUserId(UUID.randomUUID());
        payment.setWalletId(UUID.randomUUID());
        payment.setBillCategory(BillCategory.ELECTRICITY);
        payment.setServiceCode("powerco");
        payment.setReferenceNumber("REF-001");
        payment.setAmount(new BigDecimal("1250.00"));
        payment.setCurrency("TZS");
        payment.setCustomerPhone("255700000001");
        payment.setCustomerName("Test Customer");
        payment.setMetadata("{\"category\":\"bill\", \"attempt\":1}");

        BillPayment created = billPaymentRepository.createPayment(payment);

        assertThat(created.getPaymentId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(created.getMetadata()).isEqualTo("{\"category\":\"bill\",\"attempt\":1}");

        assertThat(billPaymentRepository.getPaymentById(created.getPaymentId()))
                .isPresent()
                .get()
                .extracting(BillPayment::getReferenceNumber, BillPayment::getStatus)
                .containsExactly("REF-001", PaymentStatus.INITIATED);

        assertThat(billPaymentRepository.getPaymentByReference("REF-001"))
                .isPresent()
                .get()
                .extracting(BillPayment::getAmount, BillPayment::getCurrency)
                .containsExactly(new BigDecimal("1250.00"), "TZS");
    }

    @Test
    void updatePaymentStatusShouldLockAndUpdateRow() {
        BillPayment payment = new BillPayment();
        payment.setRequestId("req-456");
        payment.setCorrelationId("corr-456");
        payment.setIdempotencyKey("idem-456");
        payment.setUserId(UUID.randomUUID());
        payment.setWalletId(UUID.randomUUID());
        payment.setBillCategory(BillCategory.WATER);
        payment.setServiceCode("waterco");
        payment.setReferenceNumber("REF-002");
        payment.setAmount(new BigDecimal("75.00"));
        payment.setCurrency("TZS");
        payment.setMetadata("{\"category\":\"bill\", \"attempt\":2}");

        BillPayment created = billPaymentRepository.createPayment(payment);

        BillPayment updated = billPaymentRepository.updatePaymentStatus(created.getPaymentId(), PaymentStatus.SUCCESS);

        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(billPaymentRepository.getPaymentById(created.getPaymentId()))
                .isPresent()
                .get()
                .extracting(BillPayment::getStatus)
                .isEqualTo(PaymentStatus.SUCCESS);
        assertThat(billPaymentRepository.countByStatus(PaymentStatus.SUCCESS)).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void createPaymentShouldBeIdempotentByIdempotencyKey() {
        String idempotencyKey = "idem-replay-001";

        BillPayment first = new BillPayment();
        first.setRequestId("req-replay-001");
        first.setCorrelationId("corr-replay-001");
        first.setIdempotencyKey(idempotencyKey);
        first.setUserId(UUID.randomUUID());
        first.setWalletId(UUID.randomUUID());
        first.setBillCategory(BillCategory.AIRTIME);
        first.setServiceCode("airtimeco");
        first.setReferenceNumber("REF-REPLAY-001");
        first.setAmount(new BigDecimal("20.00"));
        first.setCurrency("TZS");
        first.setMetadata("{}");

        BillPayment second = new BillPayment();
        second.setRequestId("req-replay-002");
        second.setCorrelationId("corr-replay-002");
        second.setIdempotencyKey(idempotencyKey);
        second.setUserId(UUID.randomUUID());
        second.setWalletId(UUID.randomUUID());
        second.setBillCategory(BillCategory.AIRTIME);
        second.setServiceCode("airtimeco");
        second.setReferenceNumber("REF-REPLAY-002");
        second.setAmount(new BigDecimal("30.00"));
        second.setCurrency("TZS");
        second.setMetadata("{}");

        BillPayment createdFirst = billPaymentRepository.createPayment(first);
        BillPayment createdSecond = billPaymentRepository.createPayment(second);

        assertThat(createdSecond.getPaymentId()).isEqualTo(createdFirst.getPaymentId());
        assertThat(createdSecond.getRequestId()).isEqualTo(createdFirst.getRequestId());
        assertThat(createdSecond.getReferenceNumber()).isEqualTo(createdFirst.getReferenceNumber());
    }

    @Test
    void createPaymentShouldRejectMissingBillCategory() {
        BillPayment payment = new BillPayment();
        payment.setRequestId("req-missing-category");
        payment.setCorrelationId("corr-missing-category");
        payment.setIdempotencyKey("idem-missing-category");
        payment.setUserId(UUID.randomUUID());
        payment.setWalletId(UUID.randomUUID());
        payment.setServiceCode("service");
        payment.setReferenceNumber("REF-MISSING-CATEGORY");
        payment.setAmount(new BigDecimal("5.00"));
        payment.setCurrency("TZS");
        payment.setMetadata("{}");

        assertThatThrownBy(() -> billPaymentRepository.createPayment(payment))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("billCategory must not be null");
    }
}