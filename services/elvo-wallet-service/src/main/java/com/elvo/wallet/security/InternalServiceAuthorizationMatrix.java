package com.elvo.wallet.security;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class InternalServiceAuthorizationMatrix {

    private static final String BASE = "/api/v1/internal/wallets/*";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, List<EndpointRule>> allowlist = Map.of(
            "identity-service", List.of(
                    new EndpointRule("GET", BASE + "/balance")
            ),
            "billing-service", List.of(
                    new EndpointRule("POST", BASE + "/reserve"),
                    new EndpointRule("POST", BASE + "/release"),
                    new EndpointRule("POST", BASE + "/confirm-debit")
            ),
            "saga-service", List.of(
                    new EndpointRule("POST", BASE + "/reverse")
            )
    );

    public boolean isAllowed(String sourceService, String method, String requestPath) {
        if (sourceService == null || method == null || requestPath == null) {
            return false;
        }

        List<EndpointRule> rules = allowlist.get(sourceService.toLowerCase());
        if (rules == null || rules.isEmpty()) {
            return false;
        }

        return rules.stream().anyMatch(rule -> rule.matches(method, requestPath, pathMatcher));
    }

    record EndpointRule(String method, String pathPattern) {
        boolean matches(String requestMethod, String requestPath, AntPathMatcher matcher) {
            return method.equalsIgnoreCase(requestMethod) && matcher.match(pathPattern, requestPath);
        }
    }
}