package com.elvo.wallet.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "elvo.clients.identity")
public class IdentityClientProperties {

    private String baseUrl = "http://localhost:8081/internal";
    private String sourceServiceName = "wallet-service";
    private String clientSourceIp = "wallet-service";
    private String clientSourceUserAgent = "wallet-service-client";
    private long tokenTtlSeconds = 60;
    private long connectTimeoutSeconds = 3;
    private long readTimeoutSeconds = 5;
    private long kycReverificationWindowDays = 30;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSourceServiceName() {
        return sourceServiceName;
    }

    public void setSourceServiceName(String sourceServiceName) {
        this.sourceServiceName = sourceServiceName;
    }

    public String getClientSourceIp() {
        return clientSourceIp;
    }

    public void setClientSourceIp(String clientSourceIp) {
        this.clientSourceIp = clientSourceIp;
    }

    public String getClientSourceUserAgent() {
        return clientSourceUserAgent;
    }

    public void setClientSourceUserAgent(String clientSourceUserAgent) {
        this.clientSourceUserAgent = clientSourceUserAgent;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public long getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(long connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public long getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(long readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public long getKycReverificationWindowDays() {
        return kycReverificationWindowDays;
    }

    public void setKycReverificationWindowDays(long kycReverificationWindowDays) {
        this.kycReverificationWindowDays = kycReverificationWindowDays;
    }
}
