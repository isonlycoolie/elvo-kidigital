package com.elvo.identity.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.identity.dto.request.EacGenerateRequest;
import com.elvo.identity.dto.request.EacVerifyRequest;
import com.elvo.identity.dto.response.EacGenerateResponse;
import com.elvo.identity.exception.ApiResponse;
import com.elvo.identity.service.EacManagementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/eac")
@Validated
public class EacController {

    private final EacManagementService eacManagementService;

    public EacController(EacManagementService eacManagementService) {
        this.eacManagementService = eacManagementService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EacGenerateResponse>> generate(@Valid @RequestBody EacGenerateRequest request) {
        EacGenerateResponse response = eacManagementService.generateEac(request);
        return ResponseEntity.ok(ApiResponse.ok("EAC generated", response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify(@Valid @RequestBody EacVerifyRequest request) {
        boolean verified = eacManagementService.verifyEac(request);
        return ResponseEntity.ok(ApiResponse.ok("EAC verification completed", Map.of("verified", verified)));
    }
}
