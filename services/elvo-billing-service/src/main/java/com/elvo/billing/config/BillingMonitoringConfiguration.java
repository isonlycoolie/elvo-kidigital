package com.elvo.billing.config;

import com.elvo.billing.monitoring.SentryAlertProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SentryAlertProperties.class)
public class BillingMonitoringConfiguration {
}
