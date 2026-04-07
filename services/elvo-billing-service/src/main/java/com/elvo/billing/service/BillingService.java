package com.elvo.billing.service;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;

/**
 * Core billing service orchestrating payment lookups, creation, execution, and reversals.
 * 
 * All methods enforce idempotency via idempotencyKey in the request DTO.
 */
public interface BillingService {

    /**
     * Create a new payment request in PENDING status.
     * 
     * Validates the request DTO, assigns a unique requestId, and persists a BillPayment record.
     * Does not interact with adapters; purely a persistence operation.
     * 
     * Idempotent: Returns the existing payment if idempotencyKey already exists.
     * 
     * @param paymentRequest the payment request DTO with required fields and idempotencyKey.
     * @return PaymentResponseDto with paymentId, status=PENDING, and metadata.
     * @throws PaymentValidationException if validation fails.
     * @throws PaymentAlreadyExistsException if idempotencyKey already exists with a different payload.
     */
    PaymentResponseDto createPayment(UtilityPaymentRequestDto paymentRequest);

    /**
     * Look up a bill or customer information via the adapter layer.
     * 
     * Validates the request, resolves the adapter for the serviceCode, calls adapter.lookup(),
     * persists a BillLookup record, and returns LookupResponseDto.
     * 
     * Does not execute payment; purely informational.
     * 
     * @param lookupRequest the lookup request DTO with serviceCode and referenceNumber.
     * @return LookupResponseDto with customer, amount, and bill details.
     * @throws PaymentValidationException if validation fails.
     * @throws BillingAdapterException if adapter lookup fails.
     */
    LookupResponseDto lookupPayment(UtilityPaymentRequestDto lookupRequest);

    /**
     * Execute a previously created payment.
     * 
     * Validates that the payment exists and is in PENDING status, resolves the adapter,
     * calls adapter.pay(), updates status to SUCCESS (or FAILED), persists the completion,
     * publishes an event, and returns PaymentResponseDto.
     * 
     * Idempotent: Returns the existing result if already executed.
     * 
     * @param paymentRequest the payment request DTO with paymentId and idempotencyKey.
     * @return PaymentResponseDto with updated status and receipt information.
     * @throws PaymentValidationException if validation fails.
     * @throws PaymentNotFoundException if payment does not exist.
     * @throws PaymentAlreadyExecutedException if payment has already been executed.
     * @throws BillingAdapterException if adapter execution fails.
     */
    PaymentResponseDto executePayment(UtilityPaymentRequestDto paymentRequest);

    /**
     * Reverse a previously executed payment.
     * 
     * Validates that the payment exists and is in SUCCESS status, creates a reversal record,
     * publishes a reversal event, and returns PaymentResponseDto with status=REVERSED.
     * 
     * Idempotent: Returns the existing reversal if already reversed.
     * 
     * @param reversalRequest the reversal request DTO with paymentId and idempotencyKey.
     * @return PaymentResponseDto with status=REVERSED and reversal metadata.
     * @throws PaymentValidationException if validation fails.
     * @throws PaymentNotFoundException if payment does not exist.
     * @throws PaymentNotReversibleException if payment is not in SUCCESS status.
     */
    PaymentResponseDto reversePayment(UtilityPaymentRequestDto reversalRequest);
}
