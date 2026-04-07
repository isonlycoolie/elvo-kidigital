package com.elvo.billing.service.orchestration;

import java.util.UUID;

import com.elvo.billing.audit.LookupAuditLogger;
import com.elvo.billing.client.BillingAdapter;
import com.elvo.billing.client.ProviderResolver;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.entity.BillLookup;
import com.elvo.billing.entity.PaymentHistory;
import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.monitoring.BillingMetricsRecorder;
import com.elvo.billing.monitoring.SentryBreadcrumbLogger;
import com.elvo.billing.monitoring.SentryErrorCapture;
import com.elvo.billing.repository.BillLookupRepository;
import com.elvo.billing.repository.PaymentHistoryRepository;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.validator.UtilityPaymentValidator;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import org.springframework.stereotype.Component;

@Component
public class LookupFlow {

    private final UtilityPaymentValidator validator;
    private final ProviderResolver providerResolver;
    private final BillLookupRepository billLookupRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final BillingEventPublisher billingEventPublisher;
    private final LookupAuditLogger lookupAuditLogger;
    private final BillingMetricsRecorder billingMetricsRecorder;
    private final SentryErrorCapture sentryErrorCapture;
    private final SentryBreadcrumbLogger sentryBreadcrumbLogger;

    public LookupFlow(
            UtilityPaymentValidator validator,
            ProviderResolver providerResolver,
            BillLookupRepository billLookupRepository,
            PaymentHistoryRepository paymentHistoryRepository,
            BillingEventPublisher billingEventPublisher,
            LookupAuditLogger lookupAuditLogger,
            BillingMetricsRecorder billingMetricsRecorder,
            SentryErrorCapture sentryErrorCapture,
            SentryBreadcrumbLogger sentryBreadcrumbLogger) {
        this.validator = validator;
        this.providerResolver = providerResolver;
        this.billLookupRepository = billLookupRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.billingEventPublisher = billingEventPublisher;
        this.lookupAuditLogger = lookupAuditLogger;
        this.billingMetricsRecorder = billingMetricsRecorder;
        this.sentryErrorCapture = sentryErrorCapture;
        this.sentryBreadcrumbLogger = sentryBreadcrumbLogger;
    }

    public LookupResponseDto execute(
            UtilityPaymentRequestDto lookupRequest,
            BillCategory billCategory,
            String serviceCode,
            String requestId,
            String correlationId) {
        ITransaction transaction = Sentry.startTransaction("billing.lookup.execute", "service");
        long startNanos = System.nanoTime();
        sentryBreadcrumbLogger.addLookupBreadcrumb("validation", lookupRequest.getReferenceNumber(), serviceCode);
        validator.validateForLookup(lookupRequest, billCategory);

        BillingAdapter adapter = providerResolver.resolve(serviceCode);
        sentryBreadcrumbLogger.addLookupBreadcrumb("execution", lookupRequest.getReferenceNumber(), serviceCode);
        LookupResponseDto adapterResponse;
        ISpan adapterSpan = transaction.startChild("adapter.call", "billing adapter lookup");
        try {
            adapterResponse = adapter.lookup(lookupRequest);
            adapterSpan.setStatus(SpanStatus.OK);
        } catch (RuntimeException ex) {
            adapterSpan.setThrowable(ex);
            adapterSpan.setStatus(SpanStatus.INTERNAL_ERROR);
            sentryErrorCapture.captureLookupFailure(serviceCode, lookupRequest.getReferenceNumber(), ex);
            billingMetricsRecorder.recordLookupOutcome(LookupStatus.FAILED, System.nanoTime() - startNanos);
            transaction.setThrowable(ex);
            transaction.setStatus(SpanStatus.INTERNAL_ERROR);
            adapterSpan.finish();
            transaction.finish();
            throw ex;
        }
        adapterSpan.finish();

        BillLookup lookup = new BillLookup();
        lookup.setLookupId(UUID.randomUUID());
        lookup.setRequestId(normalizeRequestValue(requestId));
        lookup.setBillCategory(billCategory);
        lookup.setServiceCode(serviceCode);
        lookup.setReferenceNumber(lookupRequest.getReferenceNumber());
        lookup.setCustomerPhone(lookupRequest.getCustomerPhone());
        lookup.setMetadata(lookupRequest.getMetadata());
        lookup.setLookupStatus(adapterResponse.getLookupStatus() == null ? LookupStatus.FAILED : adapterResponse.getLookupStatus());
        lookup.setCustomerName(adapterResponse.getCustomerName());
        lookup.setAmount(adapterResponse.getAmount());
        lookup.setCurrency(adapterResponse.getCurrency());
        lookup.setDescription(adapterResponse.getDescription());
        lookup.setBillItems(adapterResponse.getBillItems());
        lookup.setRawProviderReference(adapterResponse.getRawProviderReference());
        ISpan lookupDbSpan = transaction.startChild("db.query", "save bill lookup");
        billLookupRepository.save(lookup);
        lookupDbSpan.setStatus(SpanStatus.OK);
        lookupDbSpan.finish();
        lookupAuditLogger.logLookupExecuted(lookup);

        PaymentHistory history = new PaymentHistory();
        history.setPaymentId(UUID.nameUUIDFromBytes(("LOOKUP|" + lookup.getRequestId()).getBytes()));
        history.setRequestId(lookup.getRequestId());
        history.setCorrelationId(normalizeRequestValue(correlationId));
        history.setEventType("LOOKUP_EXECUTED");
        history.setFromStatus("REQUESTED");
        history.setToStatus(lookup.getLookupStatus().name());
        history.setAdapterName(serviceCode);
        history.setAdapterReference(adapterResponse.getRawProviderReference());
        history.setResponseCode(lookup.getLookupStatus().name());
        history.setResponseMessage(adapterResponse.getDescription());
        history.setMetadata(lookupRequest.getMetadata() == null ? "{}" : lookupRequest.getMetadata());
        ISpan historyDbSpan = transaction.startChild("db.query", "save lookup history");
        paymentHistoryRepository.save(history);
        historyDbSpan.setStatus(SpanStatus.OK);
        historyDbSpan.finish();

        billingEventPublisher.publish("billing.lookup.completed", lookup.getRequestId(), lookupRequest.getMetadata(), "v1");
        sentryBreadcrumbLogger.addLookupBreadcrumb("completed", lookup.getReferenceNumber(), serviceCode);
        billingMetricsRecorder.recordLookupOutcome(lookup.getLookupStatus(), System.nanoTime() - startNanos);
        transaction.setStatus(SpanStatus.OK);
        transaction.finish();

        return adapterResponse;
    }

    private static String normalizeRequestValue(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }
}
