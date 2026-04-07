package com.elvo.billing.config;

import io.sentry.SentryOptions;
import io.sentry.spring.jakarta.SentryOptionsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryConfiguration {

    @Bean
    public SentryOptionsConfiguration<SentryOptions> sentryOptionsConfiguration() {
        return options -> {
            options.setTag("service", "elvo-billing-service");
            options.setTag("module", "billing");
        };
    }
}
