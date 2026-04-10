package com.elvo.accountmanagement.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccountAuditMessagingProperties.class)
public class AccountAuditMessagingConfig {
}
