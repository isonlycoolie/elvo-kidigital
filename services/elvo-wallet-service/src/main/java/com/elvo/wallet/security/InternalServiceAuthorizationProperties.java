package com.elvo.wallet.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InternalServiceAuthorizationProperties {

    /**
     * Map of service -> list of rules in the format METHOD:/path/pattern.
     */
    private Map<String, List<String>> serviceRules = new LinkedHashMap<>();

    public Map<String, List<String>> getServiceRules() {
        return serviceRules;
    }

    public void setServiceRules(Map<String, List<String>> serviceRules) {
        this.serviceRules = serviceRules != null ? serviceRules : new LinkedHashMap<>();
    }

    public List<String> rulesFor(String service) {
        if (service == null) {
            return List.of();
        }
        return serviceRules.getOrDefault(service.toLowerCase(), new ArrayList<>());
    }
}