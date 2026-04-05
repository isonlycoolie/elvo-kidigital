package com.elvo.identity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.identity.dto.request.FastLoginGenerateRequest;
import com.elvo.identity.dto.request.FastLoginVerifyRequest;
import com.elvo.identity.dto.response.FastLoginChallengeResponse;
import com.elvo.identity.dto.response.FastLoginResponse;
import com.elvo.identity.exception.ApiResponse;
import com.elvo.identity.service.FastLoginService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/fast-login")
@Validated
public class FastLoginController {

    private final FastLoginService fastLoginService;

    public FastLoginController(FastLoginService fastLoginService) {
        this.fastLoginService = fastLoginService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FastLoginChallengeResponse>> generate(@Valid @RequestBody FastLoginGenerateRequest request) {
        FastLoginChallengeResponse response = fastLoginService.generateFastLoginPin(request);
        return ResponseEntity.ok(ApiResponse.ok("Fast login PIN generated", response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<FastLoginResponse>> verify(@Valid @RequestBody FastLoginVerifyRequest request) {
        FastLoginResponse response = fastLoginService.verifyFastLogin(request);
        return ResponseEntity.ok(ApiResponse.ok("Fast login verified", response));
    }
}
