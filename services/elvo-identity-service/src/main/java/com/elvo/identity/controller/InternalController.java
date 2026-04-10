package com.elvo.identity.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.identity.dto.request.InternalVerifyEacRequest;
import com.elvo.identity.dto.request.InternalVerifyEspRequest;
import com.elvo.identity.dto.request.InternalVerifySessionRequest;
import com.elvo.identity.dto.request.EacVerifyRequest;
import com.elvo.identity.dto.request.EspVerifyRequest;
import com.elvo.identity.dto.response.ProfileResponse;
import com.elvo.identity.entity.Session;
import com.elvo.identity.entity.User;
import com.elvo.identity.exception.ApiResponse;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.EacManagementService;
import com.elvo.identity.service.EspManagementService;
import com.elvo.identity.service.IdentityAccountReadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal")
@Validated
public class InternalController {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final EspManagementService espManagementService;
    private final EacManagementService eacManagementService;
    private final IdentityAccountReadService accountReadService;

    public InternalController(UserRepository userRepository,
                              SessionRepository sessionRepository,
                              EspManagementService espManagementService,
                              EacManagementService eacManagementService,
                              IdentityAccountReadService accountReadService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.espManagementService = espManagementService;
        this.eacManagementService = eacManagementService;
        this.accountReadService = accountReadService;
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String resolvedEan = accountReadService.resolveEan(user.getId());
        ProfileResponse response = new ProfileResponse(
                user.getId(),
            resolvedEan,
                user.getEmail(),
                user.getPhone(),
                user.getDisplayName(),
                user.isMfaEnabled(),
                user.isEspEnabled());
        return ResponseEntity.ok(ApiResponse.ok("User loaded", response));
    }

    @PostMapping("/verify-session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifySession(@Valid @RequestBody InternalVerifySessionRequest request) {
        Session session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        boolean trustedDevice = session.getDevice().isTrusted() && !session.getDevice().isRevoked() && !session.getDevice().isSuspicious();
        boolean valid = session.isActive()
            && !session.isRevoked()
            && session.getExpiresAt().isAfter(java.time.Instant.now())
            && trustedDevice;
        return ResponseEntity.ok(ApiResponse.ok("Session verification completed", Map.of(
                "verified", valid,
                "sessionId", session.getId(),
                "userId", session.getUser().getId(),
                "deviceId", session.getDevice().getId(),
            "trustedDevice", trustedDevice,
                "status", session.getSessionStatus().name())));
    }

    @PostMapping("/verify-esp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEsp(@Valid @RequestBody InternalVerifyEspRequest request) {
        EspVerifyRequest verifyRequest = new EspVerifyRequest();
        verifyRequest.setUserId(request.getUserId());
        verifyRequest.setEspCode(request.getEspCode());
        verifyRequest.setSourceIp(request.getSourceIp());
        verifyRequest.setSourceUserAgent(request.getSourceUserAgent());

        boolean verified = espManagementService.verifyEsp(verifyRequest);
        return ResponseEntity.ok(ApiResponse.ok("ESP verification completed", Map.of("verified", verified)));
    }

    @PostMapping("/verify-eac")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEac(@Valid @RequestBody InternalVerifyEacRequest request) {
        EacVerifyRequest verifyRequest = new EacVerifyRequest();
        verifyRequest.setUserId(request.getUserId());
        verifyRequest.setSessionId(request.getSessionId());
        verifyRequest.setDeviceId(request.getDeviceId());
        verifyRequest.setEacCode(request.getEacCode());
        verifyRequest.setAction(request.getAction());
        verifyRequest.setSourceIp(request.getSourceIp());
        verifyRequest.setSourceUserAgent(request.getSourceUserAgent());

        boolean verified = eacManagementService.verifyEac(verifyRequest);
        return ResponseEntity.ok(ApiResponse.ok("EAC verification completed", Map.of("verified", verified)));
    }
}
