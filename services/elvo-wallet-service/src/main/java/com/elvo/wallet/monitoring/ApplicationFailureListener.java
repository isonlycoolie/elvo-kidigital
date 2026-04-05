package com.elvo.wallet.monitoring;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationFailureListener implements ApplicationListener<ApplicationFailedEvent> {

    private final SentryExceptionReporter sentryExceptionReporter;

    public ApplicationFailureListener(SentryExceptionReporter sentryExceptionReporter) {
        this.sentryExceptionReporter = sentryExceptionReporter;
    }

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        if (event.getException() != null) {
            sentryExceptionReporter.captureUnhandledException(event.getException(), "application-startup");
        }
    }
}