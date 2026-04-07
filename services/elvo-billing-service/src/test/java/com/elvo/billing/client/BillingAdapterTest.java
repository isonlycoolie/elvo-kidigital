package com.elvo.billing.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import org.junit.jupiter.api.Test;

class BillingAdapterTest {

    @Test
    void contractShouldSupportLookupAndPayOperations() {
        BillingAdapter adapter = new BillingAdapter() {
            @Override
            public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
                return new LookupResponseDto();
            }

            @Override
            public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
                return new PaymentResponseDto();
            }
        };

        assertThat(adapter.lookup(new UtilityPaymentRequestDto())).isNotNull();
        assertThat(adapter.pay(new UtilityPaymentRequestDto())).isNotNull();
    }
}