package com.elvo.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.elvo.identity.client.AccountReadClientProperties;

@Configuration
@EnableConfigurationProperties(AccountReadClientProperties.class)
public class AccountReadClientConfig {
}
