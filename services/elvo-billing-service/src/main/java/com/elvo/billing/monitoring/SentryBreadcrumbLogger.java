package com.elvo.billing.monitoring;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import org.springframework.stereotype.Component;

@Component
public class SentryBreadcrumbLogger {

    public void addPaymentBreadcrumb(String stage, String referenceNumber, String serviceCode) {
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setCategory("billing.payment");
        breadcrumb.setType("process");
        breadcrumb.setLevel(io.sentry.SentryLevel.INFO);
        breadcrumb.setMessage("payment lifecycle stage: " + stage);
        breadcrumb.setData("stage", stage);
        breadcrumb.setData("referenceNumber", defaultValue(referenceNumber));
        breadcrumb.setData("serviceCode", defaultValue(serviceCode));
        Sentry.addBreadcrumb(breadcrumb);
    }

    public void addLookupBreadcrumb(String stage, String referenceNumber, String serviceCode) {
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setCategory("billing.lookup");
        breadcrumb.setType("process");
        breadcrumb.setLevel(io.sentry.SentryLevel.INFO);
        breadcrumb.setMessage("lookup lifecycle stage: " + stage);
        breadcrumb.setData("stage", stage);
        breadcrumb.setData("referenceNumber", defaultValue(referenceNumber));
        breadcrumb.setData("serviceCode", defaultValue(serviceCode));
        Sentry.addBreadcrumb(breadcrumb);
    }

    public void addCallbackBreadcrumb(String stage, String referenceNumber, String callbackStatus) {
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setCategory("billing.callback");
        breadcrumb.setType("process");
        breadcrumb.setLevel(io.sentry.SentryLevel.INFO);
        breadcrumb.setMessage("provider callback stage: " + stage);
        breadcrumb.setData("stage", stage);
        breadcrumb.setData("referenceNumber", defaultValue(referenceNumber));
        breadcrumb.setData("status", defaultValue(callbackStatus));
        Sentry.addBreadcrumb(breadcrumb);
    }

    private static String defaultValue(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}