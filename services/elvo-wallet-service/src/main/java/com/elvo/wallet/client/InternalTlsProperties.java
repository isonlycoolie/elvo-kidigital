package com.elvo.wallet.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "elvo.clients.identity.tls")
public class InternalTlsProperties {

    private boolean enforceHttps = true;
    private boolean enforceMtls = true;
    private String fingerprintHeader = "X-TLS-CERT-SHA256";
    private List<String> pinnedFingerprints = new ArrayList<>();

    public boolean isEnforceHttps() {
        return enforceHttps;
    }

    public void setEnforceHttps(boolean enforceHttps) {
        this.enforceHttps = enforceHttps;
    }

    public boolean isEnforceMtls() {
        return enforceMtls;
    }

    public void setEnforceMtls(boolean enforceMtls) {
        this.enforceMtls = enforceMtls;
    }

    public String getFingerprintHeader() {
        return fingerprintHeader;
    }

    public void setFingerprintHeader(String fingerprintHeader) {
        this.fingerprintHeader = fingerprintHeader;
    }

    public List<String> getPinnedFingerprints() {
        return pinnedFingerprints;
    }

    public void setPinnedFingerprints(List<String> pinnedFingerprints) {
        this.pinnedFingerprints = pinnedFingerprints == null ? new ArrayList<>() : pinnedFingerprints;
    }

    public boolean hasPinnedFingerprints() {
        return pinnedFingerprints != null && pinnedFingerprints.stream().anyMatch(v -> v != null && !v.isBlank());
    }
}
