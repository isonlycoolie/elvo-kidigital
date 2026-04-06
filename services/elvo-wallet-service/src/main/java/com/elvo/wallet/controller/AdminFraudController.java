package com.elvo.wallet.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.wallet.dto.request.FraudOverrideRequestDto;
import com.elvo.wallet.dto.request.MakerCheckerDecisionRequestDto;
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

    public AdminFraudController(FraudRulesEngine fraudRulesEngine,
                                MakerCheckerApprovalService makerCheckerApprovalService) {
        this.fraudRulesEngine = fraudRulesEngine;
        this.makerCheckerApprovalService = makerCheckerApprovalService;
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
}
