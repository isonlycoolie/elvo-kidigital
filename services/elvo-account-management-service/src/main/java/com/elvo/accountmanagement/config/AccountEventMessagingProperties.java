package com.elvo.accountmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elvo.messaging.account")
public class AccountEventMessagingProperties {

    private String exchange = "elvo.account.exchange";
    private String lifecycleRoutingKey = "account.lifecycle.v1";
    private String policyRoutingKey = "account.policy.v1";

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getLifecycleRoutingKey() {
        return lifecycleRoutingKey;
    }

    public void setLifecycleRoutingKey(String lifecycleRoutingKey) {
        this.lifecycleRoutingKey = lifecycleRoutingKey;
    }

    public String getPolicyRoutingKey() {
        return policyRoutingKey;
    }

    public void setPolicyRoutingKey(String policyRoutingKey) {
        this.policyRoutingKey = policyRoutingKey;
    }
}
