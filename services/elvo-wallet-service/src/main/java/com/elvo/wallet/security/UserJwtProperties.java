package com.elvo.wallet.security;

public class UserJwtProperties {

    private String secret = "";
    private String issuer = "elvo-identity-service";
    private String audience = "elvo-platform";
    private String signingPublicKeyPem = "";
    private String signingKeyId = "";
    private String previousSigningPublicKeyPem = "";
    private String previousSigningKeyId = "";

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

    public String getSigningPublicKeyPem() {
        return signingPublicKeyPem;
    }

    public void setSigningPublicKeyPem(String signingPublicKeyPem) {
        this.signingPublicKeyPem = signingPublicKeyPem;
    }

    public String getSigningKeyId() {
        return signingKeyId;
    }

    public void setSigningKeyId(String signingKeyId) {
        this.signingKeyId = signingKeyId;
    }

    public String getPreviousSigningPublicKeyPem() {
        return previousSigningPublicKeyPem;
    }

    public void setPreviousSigningPublicKeyPem(String previousSigningPublicKeyPem) {
        this.previousSigningPublicKeyPem = previousSigningPublicKeyPem;
    }

    public String getPreviousSigningKeyId() {
        return previousSigningKeyId;
    }

    public void setPreviousSigningKeyId(String previousSigningKeyId) {
        this.previousSigningKeyId = previousSigningKeyId;
    }
}
