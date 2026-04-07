package com.elvo.billing.client;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ProviderResolver {

    private final Map<String, BillingAdapter> adapters = new ConcurrentHashMap<>();

    public ProviderResolver() {
    }

    public ProviderResolver(Map<String, BillingAdapter> adapters) {
        if (adapters != null) {
            this.adapters.putAll(adapters);
        }
    }

    public void register(String serviceCode, BillingAdapter adapter) {
        String normalizedServiceCode = normalize(serviceCode);
        adapters.put(normalizedServiceCode, Objects.requireNonNull(adapter, "adapter must not be null"));
    }

    public BillingAdapter resolve(String serviceCode) {
        String normalizedServiceCode = normalize(serviceCode);
        BillingAdapter adapter = adapters.get(normalizedServiceCode);
        if (adapter == null) {
            throw new IllegalArgumentException("No billing adapter registered for serviceCode: " + normalizedServiceCode);
        }
        return adapter;
    }

    public boolean supports(String serviceCode) {
        return adapters.containsKey(normalize(serviceCode));
    }

    private static String normalize(String serviceCode) {
        if (serviceCode == null || serviceCode.isBlank()) {
            throw new IllegalArgumentException("serviceCode is required");
        }
        return serviceCode.trim().toUpperCase();
    }
}