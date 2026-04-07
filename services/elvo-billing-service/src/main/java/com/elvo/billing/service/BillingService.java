package com.elvo.billing.service;

import java.util.UUID;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;

public interface BillingService {

    PaymentResponseDto createPayment(UtilityPaymentRequestDto paymentRequest);

    LookupResponseDto lookupPayment(UtilityPaymentRequestDto lookupRequest);

    PaymentResponseDto executePayment(UtilityPaymentRequestDto paymentRequest);

    PaymentResponseDto reversePayment(UtilityPaymentRequestDto reversalRequest);

    PaymentResponseDto findPaymentById(UUID paymentId);

    PaymentResponseDto findPaymentByReference(String referenceNumber);
}
