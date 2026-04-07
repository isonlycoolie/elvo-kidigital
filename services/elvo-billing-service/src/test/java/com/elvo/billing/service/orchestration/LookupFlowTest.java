package com.elvo.billing.service.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.elvo.billing.client.BillingAdapter;
import com.elvo.billing.client.ProviderResolver;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.entity.BillLookup;
import com.elvo.billing.entity.PaymentHistory;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.repository.BillLookupRepository;
import com.elvo.billing.repository.PaymentHistoryRepository;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.validator.UtilityPaymentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LookupFlowTest {

    @Mock
    private UtilityPaymentValidator validator;

    @Mock
    private ProviderResolver providerResolver;

    @Mock
    private BillLookupRepository billLookupRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private BillingAdapter adapter;

    @Mock
    private BillingEventPublisher billingEventPublisher;

    @Test
    void shouldExecuteLookupAndPersistLookupHistory() {
        LookupFlow flow = new LookupFlow(
            validator,
            providerResolver,
            billLookupRepository,
            paymentHistoryRepository,
            billingEventPublisher);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("LOOKUP-001");
        request.setAmount(BigDecimal.valueOf(900));
        request.setMetadata("{}");

        LookupResponseDto adapterResponse = new LookupResponseDto();
        adapterResponse.setLookupStatus(LookupStatus.SUCCESS);
        adapterResponse.setCustomerName("Lookup Customer");
        adapterResponse.setReferenceNumber("LOOKUP-001");
        adapterResponse.setAmount(BigDecimal.valueOf(900));
        adapterResponse.setCurrency("TZS");
        adapterResponse.setDescription("lookup ok");
        adapterResponse.setBillItems("[]");
        adapterResponse.setRawProviderReference("RAW-1");

        when(providerResolver.resolve("LUKU")).thenReturn(adapter);
        when(adapter.lookup(request)).thenReturn(adapterResponse);

        LookupResponseDto result = flow.execute(
                request,
                BillCategory.ELECTRICITY,
                "LUKU",
                "REQ-L-1",
                "CORR-L-1");

        assertThat(result.getLookupStatus()).isEqualTo(LookupStatus.SUCCESS);
        verify(validator).validateForLookup(eq(request), eq(BillCategory.ELECTRICITY));
        verify(providerResolver).resolve("LUKU");
        verify(adapter).lookup(request);

        ArgumentCaptor<BillLookup> lookupCaptor = ArgumentCaptor.forClass(BillLookup.class);
        verify(billLookupRepository).save(lookupCaptor.capture());
        assertThat(lookupCaptor.getValue().getServiceCode()).isEqualTo("LUKU");
        assertThat(lookupCaptor.getValue().getLookupStatus()).isEqualTo(LookupStatus.SUCCESS);

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEventType()).isEqualTo("LOOKUP_EXECUTED");
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo("SUCCESS");
        verify(billingEventPublisher).publish(eq("billing.lookup.completed"), eq("REQ-L-1"), eq("{}"));
    }
}
