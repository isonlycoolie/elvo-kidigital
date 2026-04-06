package com.elvo.wallet.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.wallet.monitoring.DisasterRecoveryValidationService;

@RestController
@RequestMapping("/api/v1/internal/resilience")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalResilienceController {

    private final DisasterRecoveryValidationService disasterRecoveryValidationService;

    public InternalResilienceController(DisasterRecoveryValidationService disasterRecoveryValidationService) {
        this.disasterRecoveryValidationService = disasterRecoveryValidationService;
    }

    @GetMapping("/disaster-recovery/validate")
    public ResponseEntity<DisasterRecoveryValidationService.ValidationReport> validateDisasterRecoveryReadiness() {
        DisasterRecoveryValidationService.ValidationReport report = disasterRecoveryValidationService.validateReadiness();
        return report.ready()
                ? ResponseEntity.ok(report)
                : ResponseEntity.status(503).body(report);
    }
}
