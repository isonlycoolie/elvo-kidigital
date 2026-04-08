package com.elvo.billing.security;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Component
public class BillingServiceAuthorizationMatrix {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final BillingServiceAuthorizationProperties authorizationProperties;

    public BillingServiceAuthorizationMatrix(BillingServiceAuthorizationProperties authorizationProperties) {
        this.authorizationProperties = authorizationProperties;
    }

    public boolean isAllowed(String sourceService, String method, String requestPath) {
        if (sourceService == null || method == null || requestPath == null) {
            return false;
        }

        List<EndpointRule> rules = authorizationProperties.rulesFor(sourceService).stream()
                .map(EndpointRule::fromConfig)
                .toList();

        if (rules.isEmpty()) {
            return false;
        }

        return rules.stream().anyMatch(rule -> rule.matches(method, requestPath, pathMatcher));
    }

    record EndpointRule(String method, String pathPattern) {
        static EndpointRule fromConfig(String rule) {
            if (rule == null || !rule.contains(":")) {
                return new EndpointRule("INVALID", "/__invalid__");
            }
            String[] parts = rule.split(":", 2);
            return new EndpointRule(parts[0].trim(), parts[1].trim());
        }

        boolean matches(String requestMethod, String requestPath, AntPathMatcher matcher) {
            return method.equalsIgnoreCase(requestMethod) && matcher.match(pathPattern, requestPath);
        }
    }
}
