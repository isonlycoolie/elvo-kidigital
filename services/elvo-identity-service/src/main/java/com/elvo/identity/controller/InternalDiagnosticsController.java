package com.elvo.identity.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.identity.monitoring.SentryExceptionReporter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/internal/diagnostics")
public class InternalDiagnosticsController {

    private final SentryExceptionReporter sentryExceptionReporter;
    private final boolean sentryProbeEnabled;
    private final String sentryProbeToken;

    public InternalDiagnosticsController(
            SentryExceptionReporter sentryExceptionReporter,
            @Value("${elvo.monitoring.sentry.probe.enabled:false}") boolean sentryProbeEnabled,
            @Value("${elvo.monitoring.sentry.probe.token:}") String sentryProbeToken) {
        this.sentryExceptionReporter = sentryExceptionReporter;
        this.sentryProbeEnabled = sentryProbeEnabled;
        this.sentryProbeToken = sentryProbeToken;
    }

    @PostMapping("/sentry-probe")
    public ResponseEntity<Map<String, Object>> sentryProbe(
            @RequestHeader(value = "X-Sentry-Probe-Token", required = false) String providedToken,
            HttpServletRequest request) {
        if (!sentryProbeEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "SENTRY_PROBE_DISABLED"));
        }
        if (sentryProbeToken == null || sentryProbeToken.isBlank() || !sentryProbeToken.equals(providedToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "SENTRY_PROBE_FORBIDDEN"));
        }

        IllegalStateException probeException = new IllegalStateException("identity_sentry_runtime_probe");
        sentryExceptionReporter.captureCriticalException(
                probeException,
                request,
                Map.of("probe", "runtime-wiring", "service", "identity"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "captured");
        response.put("service", "identity");
        response.put("probe", "runtime-wiring");
        return ResponseEntity.accepted().body(response);
    }
}
