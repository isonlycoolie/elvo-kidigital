package com.elvo.billing.client;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;

public interface BillingAdapter {

    LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest);

    PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest);
}