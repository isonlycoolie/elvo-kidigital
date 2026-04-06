package com.elvo.wallet.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MutualTlsProperties {

    private Boolean enabled;
    private String subjectPrincipalRegex = "CN=(.*?)(?:,|$)";
    private List<String> trustedCommonNames = new ArrayList<>();

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabledForProfiles(String[] activeProfiles) {
        if (enabled != null) {
            return enabled;
        }

        if (activeProfiles == null || activeProfiles.length == 0) {
            return true;
        }

        return Arrays.stream(activeProfiles)
                .map(String::toLowerCase)
                .noneMatch(profile -> profile.equals("local") || profile.equals("dev") || profile.equals("test"));
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