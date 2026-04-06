package com.elvo.wallet.security;

import java.util.ArrayList;
import java.util.List;

public class MutualTlsProperties {

    private boolean enabled = false;
    private String subjectPrincipalRegex = "CN=(.*?)(?:,|$)";
    private List<String> trustedCommonNames = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSubjectPrincipalRegex() {
        return subjectPrincipalRegex;
    }

    public void setSubjectPrincipalRegex(String subjectPrincipalRegex) {
        this.subjectPrincipalRegex = subjectPrincipalRegex;
    }

    public List<String> getTrustedCommonNames() {
        return trustedCommonNames;
    }

    public void setTrustedCommonNames(List<String> trustedCommonNames) {
        this.trustedCommonNames = trustedCommonNames;
    }
}