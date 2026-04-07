package com.elvo.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import org.junit.jupiter.api.Test;

class BillingServiceContractTest {

    @Test
    void shouldExposeCreatePaymentContract() throws Exception {
        Method method = BillingService.class.getMethod("createPayment", UtilityPaymentRequestDto.class);

        assertThat(method.getReturnType()).isEqualTo(PaymentResponseDto.class);
    }

    @Test
    void shouldExposeLookupPaymentContract() throws Exception {
        Method method = BillingService.class.getMethod("lookupPayment", UtilityPaymentRequestDto.class);

        assertThat(method.getReturnType()).isEqualTo(LookupResponseDto.class);
    }

    @Test
    void shouldExposeExecutePaymentContract() throws Exception {
        Method method = BillingService.class.getMethod("executePayment", UtilityPaymentRequestDto.class);

        assertThat(method.getReturnType()).isEqualTo(PaymentResponseDto.class);
    }

    @Test
    void shouldExposeReversePaymentContract() throws Exception {
        Method method = BillingService.class.getMethod("reversePayment", UtilityPaymentRequestDto.class);

        assertThat(method.getReturnType()).isEqualTo(PaymentResponseDto.class);
    }
}
