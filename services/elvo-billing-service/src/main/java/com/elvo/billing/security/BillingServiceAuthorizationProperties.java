package com.elvo.billing.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class BillingServiceAuthorizationProperties {

    private final Map<String, List<String>> serviceRules = new LinkedHashMap<>();

    public BillingServiceAuthorizationProperties() {
        serviceRules.put("billing-service", List.of(
                "PUBLISH:billing.transaction.requested",
                "PUBLISH:billing.transaction.completed",
                "PUBLISH:billing.payment.completed",
                "PUBLISH:billing.payment.callback.received",
                "PUBLISH:billing.payment.reversed"));
        serviceRules.put("wallet-service", List.of(
                "CONSUME:wallet.transaction.completed.queue",
                "CONSUME:wallet.transaction.failed.queue"));
    }

    public Map<String, List<String>> getServiceRules() {
        return serviceRules;
    }

    public List<String> rulesFor(String service) {
        if (service == null) {
            return List.of();
        }
        return serviceRules.getOrDefault(service.toLowerCase(), new ArrayList<>());
    }
}
