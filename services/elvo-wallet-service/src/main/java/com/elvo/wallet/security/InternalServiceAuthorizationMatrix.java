package com.elvo.wallet.security;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class InternalServiceAuthorizationMatrix {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final InternalServiceAuthorizationProperties authorizationProperties;

    public InternalServiceAuthorizationMatrix(InternalServiceAuthorizationProperties authorizationProperties) {
        this.authorizationProperties = authorizationProperties;
    }

    public boolean isAllowed(String sourceService, String method, String requestPath) {
        if (sourceService == null || method == null || requestPath == null) {
            return false;
        }

        List<EndpointRule> rules = authorizationProperties.rulesFor(sourceService).stream()
                .map(EndpointRule::fromConfig)
                .toList();

        if (rules == null || rules.isEmpty()) {
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