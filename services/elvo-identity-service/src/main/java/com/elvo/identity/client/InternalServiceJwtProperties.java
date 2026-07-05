package com.elvo.identity.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "elvo.security.internal-jwt")
public class InternalServiceJwtProperties {

    private String secret = "";
    private String issuer = "elvo-wallet-service-internal-dev";
    private String audience = "elvo-wallet-service-internal-dev";
    private String requiredRole = "INTERNAL_SERVICE";
    private String sourceServiceClaim = "sourceService";
    private String serviceIdentityClaim = "serviceIdentity";
    private int tokenTtlSeconds = 60;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    public String getSourceServiceClaim() {
        return sourceServiceClaim;
    }

    public void setSourceServiceClaim(String sourceServiceClaim) {
        this.sourceServiceClaim = sourceServiceClaim;
    }

    public String getServiceIdentityClaim() {
        return serviceIdentityClaim;
    }

    public void setServiceIdentityClaim(String serviceIdentityClaim) {
        this.serviceIdentityClaim = serviceIdentityClaim;
    }

    public int getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(int tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }
}
