package com.elvo.accountmanagement.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sentry.Sentry;

@RestController
@RequestMapping("/internal/diagnostics")
public class InternalDiagnosticsController {

    private final boolean sentryProbeEnabled;
    private final String sentryProbeToken;

    public InternalDiagnosticsController(
            @Value("${elvo.monitoring.sentry.probe.enabled:false}") boolean sentryProbeEnabled,
            @Value("${elvo.monitoring.sentry.probe.token:}") String sentryProbeToken) {
        this.sentryProbeEnabled = sentryProbeEnabled;
        this.sentryProbeToken = sentryProbeToken;
    }

    @PostMapping("/sentry-probe")
    public ResponseEntity<Map<String, Object>> sentryProbe(
            @RequestHeader(value = "X-Sentry-Probe-Token", required = false) String providedToken) {
        if (!sentryProbeEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "SENTRY_PROBE_DISABLED"));
        }
        if (sentryProbeToken == null || sentryProbeToken.isBlank() || !sentryProbeToken.equals(providedToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "SENTRY_PROBE_FORBIDDEN"));
        }

        Sentry.captureException(new IllegalStateException("account_management_sentry_runtime_probe"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "captured");
        response.put("service", "account-management");
        response.put("probe", "runtime-wiring");
        return ResponseEntity.accepted().body(response);
    }
}