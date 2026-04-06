package com.elvo.identity.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "elvo.clients.provisioning")
public class ProvisioningClientProperties {

    private String walletBaseUrl = "http://localhost:8082/internal";
    private String profileBaseUrl = "http://localhost:8083/internal";
    private String sourceServiceName = "identity-service";

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
}
