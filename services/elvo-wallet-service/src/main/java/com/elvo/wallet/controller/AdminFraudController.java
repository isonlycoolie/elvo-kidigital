package com.elvo.wallet.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.wallet.dto.request.AmlCaseResolutionRequestDto;
import com.elvo.wallet.dto.request.FraudOverrideRequestDto;
import com.elvo.wallet.dto.request.MakerCheckerDecisionRequestDto;
import com.elvo.wallet.monitoring.WalletMetricsRecorder;
import com.elvo.wallet.security.AmlCaseWorkflowService;
import com.elvo.wallet.security.FraudRulesEngine;
import com.elvo.wallet.security.MakerCheckerApprovalService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/fraud")
@Validated
@PreAuthorize("hasRole('FRAUD_ADMIN')")
public class AdminFraudController {

    private final FraudRulesEngine fraudRulesEngine;
    private final MakerCheckerApprovalService makerCheckerApprovalService;
    private final AmlCaseWorkflowService amlCaseWorkflowService;
    private final WalletMetricsRecorder walletMetricsRecorder;

    public AdminFraudController(FraudRulesEngine fraudRulesEngine,
                                MakerCheckerApprovalService makerCheckerApprovalService,
                                AmlCaseWorkflowService amlCaseWorkflowService,
                                WalletMetricsRecorder walletMetricsRecorder) {
        this.fraudRulesEngine = fraudRulesEngine;
        this.makerCheckerApprovalService = makerCheckerApprovalService;
        this.amlCaseWorkflowService = amlCaseWorkflowService;
        this.walletMetricsRecorder = walletMetricsRecorder;
    }

    @PostMapping("/overrides/users/{userId}")
    public ResponseEntity<Map<String, Object>> overrideUser(
            @PathVariable UUID userId,
            @Valid @RequestBody FraudOverrideRequestDto request) {
        fraudRulesEngine.setUserOverride(userId, request.getDecision());
        return ResponseEntity.accepted().body(Map.of(
                "scope", "user",
                "userId", userId,
                "decision", request.getDecision()));
    }

    @PostMapping("/overrides/targets/{targetIdentifier}")
    public ResponseEntity<Map<String, Object>> overrideTarget(
            @PathVariable String targetIdentifier,
            @Valid @RequestBody FraudOverrideRequestDto request) {
        fraudRulesEngine.setTargetOverride(targetIdentifier, request.getDecision());
        return ResponseEntity.accepted().body(Map.of(
                "scope", "target",
                "targetIdentifier", targetIdentifier,
                "decision", request.getDecision()));
    }

    @PostMapping("/maker-checker/{approvalId}/decision")
    public ResponseEntity<Map<String, Object>> decideMakerChecker(
            @PathVariable String approvalId,
            @Valid @RequestBody MakerCheckerDecisionRequestDto request) {
        makerCheckerApprovalService.recordDecision(approvalId, request.isApproved(), request.getReason());
        return ResponseEntity.accepted().body(Map.of(
                "approvalId", approvalId,
                "approved", request.isApproved(),
                "reason", request.getReason()));
    }

    @GetMapping("/aml-cases")
    public ResponseEntity<List<AmlCaseWorkflowService.AmlCase>> listAmlCases(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        AmlCaseWorkflowService.CaseStatus filter = parseStatus(status);
        return ResponseEntity.ok(amlCaseWorkflowService.listCases(filter, limit));
    }

    @PostMapping("/aml-cases/{caseId}/review")
    public ResponseEntity<Map<String, Object>> markAmlCaseUnderReview(@PathVariable String caseId) {
        AmlCaseWorkflowService.AmlCase amlCase = amlCaseWorkflowService.setUnderReview(caseId);
        if (amlCase == null) {
            return ResponseEntity.notFound().build();
        }
        walletMetricsRecorder.recordAmlCase("under_review", amlCase.category());
        return ResponseEntity.accepted().body(Map.of(
                "caseId", amlCase.caseId(),
                "status", amlCase.status().name()));
    }

    @PostMapping("/aml-cases/{caseId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveAmlCase(
            @PathVariable String caseId,
            @Valid @RequestBody AmlCaseResolutionRequestDto request) {
        String resolver = "fraud-admin";
        AmlCaseWorkflowService.AmlCase amlCase = amlCaseWorkflowService.resolveCase(
                caseId,
                request.isSuspiciousActivityConfirmed(),
                request.getResolutionNotes(),
                resolver);
        if (amlCase == null) {
            return ResponseEntity.notFound().build();
        }
        walletMetricsRecorder.recordAmlCase("resolved", amlCase.category());
        return ResponseEntity.accepted().body(Map.of(
                "caseId", amlCase.caseId(),
                "status", amlCase.status().name(),
                "suspiciousActivityConfirmed", amlCase.suspiciousActivityConfirmed(),
                "resolvedBy", amlCase.resolvedBy()));
    }

    private AmlCaseWorkflowService.CaseStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AmlCaseWorkflowService.CaseStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
