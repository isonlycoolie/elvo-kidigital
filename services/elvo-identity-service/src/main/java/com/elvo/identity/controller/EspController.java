package com.elvo.identity.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.identity.dto.request.EspGenerateRequest;
import com.elvo.identity.dto.request.EspVerifyRequest;
import com.elvo.identity.dto.response.EspGenerateResponse;
import com.elvo.identity.exception.ApiResponse;
import com.elvo.identity.service.EspManagementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/esp")
@Validated
public class EspController {

    private final EspManagementService espManagementService;

    public EspController(EspManagementService espManagementService) {
        this.espManagementService = espManagementService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EspGenerateResponse>> generate(@Valid @RequestBody EspGenerateRequest request) {
        EspGenerateResponse response = espManagementService.generateEsp(request);
        return ResponseEntity.ok(ApiResponse.ok("ESP generated", response));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<EspGenerateResponse>> update(@Valid @RequestBody EspGenerateRequest request) {
        EspGenerateResponse response = espManagementService.updateEsp(request);
        return ResponseEntity.ok(ApiResponse.ok("ESP updated", response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify(@Valid @RequestBody EspVerifyRequest request) {
        boolean verified = espManagementService.verifyEsp(request);
        return ResponseEntity.ok(ApiResponse.ok("ESP verification completed", Map.of("verified", verified)));
    }
}
