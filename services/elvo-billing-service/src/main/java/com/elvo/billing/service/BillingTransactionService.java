package com.elvo.billing.service;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.BillCategory;

public interface BillingTransactionService {

    PaymentResponseDto initiateTransaction(UtilityPaymentRequestDto paymentRequest, BillCategory billCategory);

    PaymentResponseDto processTransaction(String serviceCode, UtilityPaymentRequestDto paymentRequest);

    PaymentResponseDto completeTransaction(BillPayment payment);

    PaymentResponseDto reverseTransaction(BillPayment payment, String reason);
}
