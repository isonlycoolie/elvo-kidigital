package com.elvo.identity.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "elvo.clients.provisioning")
public class ProvisioningClientProperties {

    private String walletBaseUrl = "http://localhost:8082/internal";
    private String profileBaseUrl = "http://localhost:8083/internal";
    private String sourceServiceName = "identity-service";
    private String internalAuthToken = "";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private int maxAttempts = 3;
    private int retryBackoffMs = 200;
    private double retryBackoffMultiplier = 2.0;
    private int retryMaxBackoffMs = 1000;

    public String getWalletBaseUrl() {
        return walletBaseUrl;
    }

    public void setWalletBaseUrl(String walletBaseUrl) {
        this.walletBaseUrl = walletBaseUrl;
    }

    public String getProfileBaseUrl() {
        return profileBaseUrl;
    }

    public void setProfileBaseUrl(String profileBaseUrl) {
        this.profileBaseUrl = profileBaseUrl;
    }

    public String getSourceServiceName() {
        return sourceServiceName;
    }

    public void setSourceServiceName(String sourceServiceName) {
        this.sourceServiceName = sourceServiceName;
    }

    public String getInternalAuthToken() {
        return internalAuthToken;
    }

    public void setInternalAuthToken(String internalAuthToken) {
        this.internalAuthToken = internalAuthToken;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(int retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) {
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }

    public int getRetryMaxBackoffMs() {
        return retryMaxBackoffMs;
    }

    public void setRetryMaxBackoffMs(int retryMaxBackoffMs) {
        this.retryMaxBackoffMs = retryMaxBackoffMs;
    }
}
