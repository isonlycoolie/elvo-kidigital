package com.elvo.billing.service.orchestration;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.elvo.billing.client.BillingAdapter;
import com.elvo.billing.client.ProviderResolver;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.entity.BillLookup;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.exception.BillingAdapterException;
import com.elvo.billing.exception.PaymentValidationException;
import com.elvo.billing.repository.BillLookupRepository;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.validator.UtilityPaymentValidator;

/**
 * Orchestrates the lookup flow:
 * 1. Validate request DTO
 * 2. Resolve adapter for serviceCode
 * 3. Execute adapter lookup
 * 4. Persist BillLookup record
 * 5. Publish event
 */
@Component
public class LookupFlow {

    private static final Logger log = LoggerFactory.getLogger(LookupFlow.class);
    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.service");

    private final BillLookupRepository lookupRepository;
    private final ProviderResolver providerResolver;
    private final UtilityPaymentValidator validator;
    private final BillingEventPublisher eventPublisher;

    public LookupFlow(
            BillLookupRepository lookupRepository,
            ProviderResolver providerResolver,
            UtilityPaymentValidator validator,
            BillingEventPublisher eventPublisher) {
        this.lookupRepository = lookupRepository;
        this.providerResolver = providerResolver;
        this.validator = validator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Execute a lookup via the adapter layer, persist the result, and publish an event.
     * 
     * @param lookupRequest the lookup request DTO
     * @param requestId the unique request ID
     * @return LookupResponseDto from the persisted BillLookup
     * @throws PaymentValidationException if validation fails
     * @throws BillingAdapterException if adapter lookup fails
     */
    public LookupResponseDto executeLookup(UtilityPaymentRequestDto lookupRequest, String requestId) {
        try {
            validator.validateLookupRequest(lookupRequest);
            auditLog.info("Lookup validation passed: requestId={}, serviceCode={}, referenceNumber={}", 
                    requestId, lookupRequest.getServiceCode(), lookupRequest.getReferenceNumber());

            // Resolve adapter
            BillingAdapter adapter = providerResolver.resolve(lookupRequest.getServiceCode());

            // Call adapter
            LookupResponseDto adapterResponse = adapter.lookup(lookupRequest);
            auditLog.info("Adapter lookup succeeded: requestId={}, lookupStatus={}", 
                    requestId, adapterResponse.getLookupStatus());

            // Persist lookup
            BillLookup lookup = new BillLookup();
            lookup.setLookupId(UUID.randomUUID());
            lookup.setRequestId(requestId);
            lookup.setBillCategory(null); // Will be inferred from serviceCode if needed
            lookup.setServiceCode(lookupRequest.getServiceCode());
            lookup.setReferenceNumber(lookupRequest.getReferenceNumber());
            lookup.setCustomerPhone(lookupRequest.getCustomerPhone());
            lookup.setMetadata(lookupRequest.getMetadata() != null ? lookupRequest.getMetadata() : "{}");
            lookup.setLookupStatus(adapterResponse.getLookupStatus());
            lookup.setCustomerName(adapterResponse.getCustomerName());
            lookup.setAmount(adapterResponse.getAmount());
            lookup.setCurrency(adapterResponse.getCurrency());
            lookup.setDescription(adapterResponse.getDescription());
            lookup.setBillItems(adapterResponse.getBillItems());
            lookup.setRawProviderReference(adapterResponse.getRawProviderReference());
            lookup.setCreatedAt(Instant.now());
            lookup.setUpdatedAt(Instant.now());

            BillLookup persisted = lookupRepository.createLookup(lookup);
            auditLog.info("Lookup persisted: lookupId={}, lookupStatus={}", 
                    persisted.getLookupId(), persisted.getLookupStatus());

            // Publish event
            eventPublisher.publishLookupCompleted(persisted);

            // Return response
            return adapterResponse;
        } catch (PaymentValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Lookup execution failed: requestId={}, serviceCode={}, referenceNumber={}", 
                    requestId, lookupRequest.getServiceCode(), lookupRequest.getReferenceNumber(), ex);
            throw new BillingAdapterException("Lookup execution failed: " + ex.getMessage(), ex);
        }
    }
}
