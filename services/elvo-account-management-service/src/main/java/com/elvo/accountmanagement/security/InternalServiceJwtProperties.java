package com.elvo.accountmanagement.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elvo.security.internal-jwt")
public class InternalServiceJwtProperties {

    private String secret = "";
    private String issuer = "elvo-wallet-service-internal-dev";
    private String audience = "elvo-wallet-service-internal-dev";
    private String requiredRole = "INTERNAL_SERVICE";
    private String sourceServiceClaim = "sourceService";
    private String serviceIdentityClaim = "serviceIdentity";

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
}
