package com.elvo.identity.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import com.elvo.identity.monitoring.SentryExceptionReporter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
public class UnhandledExceptionCaptureConfig implements AsyncConfigurer {

    private final SentryExceptionReporter sentryExceptionReporter;
    private Thread.UncaughtExceptionHandler previousHandler;

    public UnhandledExceptionCaptureConfig(SentryExceptionReporter sentryExceptionReporter) {
        this.sentryExceptionReporter = sentryExceptionReporter;
    }

    @PostConstruct
    public void installUncaughtExceptionHandler() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            sentryExceptionReporter.captureUnhandledException(
                    throwable,
                    "thread:" + thread.getName());
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
    }

    @PreDestroy
    public void restoreUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
    }

    @Override
    public Executor getAsyncExecutor() {
        return null;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                sentryExceptionReporter.captureUnhandledException(ex, "async:" + method.getName());
            }
        };
    }
}
