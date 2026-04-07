package com.elvo.billing.service.orchestration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.elvo.billing.client.BillingAdapter;
import com.elvo.billing.client.ProviderResolver;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.exception.BillingAdapterException;
import com.elvo.billing.exception.PaymentAlreadyExecutedException;
import com.elvo.billing.exception.PaymentNotFoundException;
import com.elvo.billing.exception.PaymentValidationException;
import com.elvo.billing.mapper.GenericRequestMapper;
import com.elvo.billing.mapper.GenericResponseMapper;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.validator.UtilityPaymentValidator;

/**
 * Orchestrates the payment execution flow:
 * 1. Validate request DTO
 * 2. Resolve adapter for serviceCode
 * 3. Perform lookup if required
 * 4. Execute payment via adapter
 * 5. Update BillPayment status
 * 6. Publish events
 */
@Component
public class PaymentFlow {

    private static final Logger log = LoggerFactory.getLogger(PaymentFlow.class);
    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.service");

    private final BillPaymentRepository paymentRepository;
    private final ProviderResolver providerResolver;
    private final UtilityPaymentValidator validator;
    private final GenericRequestMapper requestMapper;
    private final GenericResponseMapper responseMapper;
    private final BillingEventPublisher eventPublisher;

    public PaymentFlow(
            BillPaymentRepository paymentRepository,
            ProviderResolver providerResolver,
            UtilityPaymentValidator validator,
            GenericRequestMapper requestMapper,
            GenericResponseMapper responseMapper,
            BillingEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.providerResolver = providerResolver;
        this.validator = validator;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Execute a payment: validate, lookup (if required), charge, update status, and publish event.
     * 
     * @param paymentRequest the payment request DTO
     * @param payment the persisted BillPayment entity in PENDING status
     * @return PaymentResponseDto with updated status and receipt
     * @throws PaymentAlreadyExecutedException if payment is not in PENDING status
     * @throws BillingAdapterException if adapter execution fails
     */
    public PaymentResponseDto executePayment(UtilityPaymentRequestDto paymentRequest, BillPayment payment) {
        validatePaymentIsExecutable(payment);

        try {
            // Resolve adapter for the service code
            BillingAdapter adapter = providerResolver.resolve(payment.getServiceCode());

            // Perform lookup if required
            if (paymentRequest.isLookupRequired()) {
                LookupResponseDto lookupResult = adapter.lookup(paymentRequest);
                auditLog.info("Lookup completed: referenceNumber={}, lookupStatus={}", 
                        payment.getReferenceNumber(), lookupResult.getLookupStatus());
            }

            // Execute payment via adapter
            PaymentResponseDto adapterResponse = adapter.pay(paymentRequest);
            auditLog.info("Payment executed: paymentId={}, externalReference={}, status={}", 
                    payment.getPaymentId(), adapterResponse.getExternalReference(), adapterResponse.getStatus());

            // Update payment entity with adapter response
            payment.setExternalReference(adapterResponse.getExternalReference());
            payment.setReceiptNumber(adapterResponse.getReceiptNumber());
            payment.setPaidAmount(adapterResponse.getPaidAmount());
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setCompletedAt(adapterResponse.getCompletedAt());
            payment.setMetadata(adapterResponse.getMetadata());

            // Persist updated payment
            BillPayment updated = paymentRepository.updatePaymentStatus(payment.getPaymentId(), PaymentStatus.SUCCESS);
            auditLog.info("Payment persisted: paymentId={}, status=SUCCESS", payment.getPaymentId());

            // Publish event
            eventPublisher.publishPaymentCompleted(updated);

            // Return success response
            return mapToPaymentResponse(updated);
        } catch (Exception ex) {
            log.error("Payment execution failed: paymentId={}, referenceNumber={}", 
                    payment.getPaymentId(), payment.getReferenceNumber(), ex);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.updatePaymentStatus(payment.getPaymentId(), PaymentStatus.FAILED);
            eventPublisher.publishPaymentFailed(payment, ex.getMessage());
            throw new BillingAdapterException("Payment execution failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Perform a lookup via the adapter layer without charging.
     * 
     * @param lookupRequest the lookup request DTO
     * @return LookupResponseDto from the adapter
     * @throws BillingAdapterException if adapter lookup fails
     * @throws PaymentValidationException if validation fails
     */
    public LookupResponseDto performLookup(UtilityPaymentRequestDto lookupRequest) {
        try {
            validator.validateLookupRequest(lookupRequest);
            BillingAdapter adapter = providerResolver.resolve(lookupRequest.getServiceCode());
            return adapter.lookup(lookupRequest);
        } catch (PaymentValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Lookup execution failed: serviceCode={}, referenceNumber={}", 
                    lookupRequest.getServiceCode(), lookupRequest.getReferenceNumber(), ex);
            throw new BillingAdapterException("Lookup execution failed: " + ex.getMessage(), ex);
        }
    }

    private void validatePaymentIsExecutable(BillPayment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentAlreadyExecutedException(
                    "Payment is not in PENDING status; current status: " + payment.getStatus());
        }
    }

    private PaymentResponseDto mapToPaymentResponse(BillPayment payment) {
        PaymentResponseDto response = new PaymentResponseDto();
        response.setPaymentId(payment.getPaymentId());
        response.setExternalReference(payment.getExternalReference());
        response.setStatus(payment.getStatus());
        response.setReceiptNumber(payment.getReceiptNumber());
        response.setPaidAmount(payment.getPaidAmount());
        response.setCurrency(payment.getCurrency());
        response.setCompletedAt(payment.getCompletedAt());
        response.setMetadata(payment.getMetadata());
        return response;
    }
}
