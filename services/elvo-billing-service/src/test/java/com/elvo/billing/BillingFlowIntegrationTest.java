package com.elvo.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.elvo.billing.client.MockAdapter;
import com.elvo.billing.client.ProviderResolver;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.PaymentHistory;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.repository.BillLookupRepository;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.repository.PaymentHistoryRepository;
import com.elvo.billing.service.BillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BillingFlowIntegrationTest {

    @Autowired
    private BillingService billingService;

    @Autowired
    private ProviderResolver providerResolver;

    @Autowired
    private MockAdapter mockAdapter;

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private BillLookupRepository billLookupRepository;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    @BeforeEach
    void setUp() {
        providerResolver.register("LUKU", mockAdapter);
    }

    @Test
    void executePaymentShouldPersistPaymentAndHistory() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("INT-PAY-001");
        request.setAmount(new BigDecimal("1550.00"));
        request.setCustomerName("Integration Customer");
        request.setMetadata("{}");

        PaymentResponseDto response = billingService.executePayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(billPaymentRepository.getPaymentByReference("INT-PAY-001")).isPresent();

        boolean hasPaymentEvent = paymentHistoryRepository.findAll().stream()
                .map(PaymentHistory::getEventType)
                .anyMatch("PAYMENT_EXECUTED"::equals);
        assertThat(hasPaymentEvent).isTrue();
    }

    @Test
    void lookupPaymentShouldPersistLookupAndHistory() {
        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("INT-LOOKUP-001");
        request.setLookupRequired(true);
        request.setMetadata("{}");

        LookupResponseDto response = billingService.lookupPayment(request);

        assertThat(response.getLookupStatus()).isEqualTo(LookupStatus.SUCCESS);
        assertThat(billLookupRepository.findAll())
                .anyMatch(lookup -> "INT-LOOKUP-001".equals(lookup.getReferenceNumber()));

        boolean hasLookupEvent = paymentHistoryRepository.findAll().stream()
                .map(PaymentHistory::getEventType)
                .anyMatch("LOOKUP_EXECUTED"::equals);
        assertThat(hasLookupEvent).isTrue();
    }
}
