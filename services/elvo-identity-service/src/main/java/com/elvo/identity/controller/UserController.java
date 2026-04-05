package com.elvo.identity.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.identity.audit.AuditEventPublisher;
import com.elvo.identity.dto.request.ProfileUpdateRequest;
import com.elvo.identity.dto.response.DeviceInfoResponse;
import com.elvo.identity.dto.response.ProfileResponse;
import com.elvo.identity.dto.response.SessionInfoResponse;
import com.elvo.identity.entity.Audit;
import com.elvo.identity.entity.Device;
import com.elvo.identity.entity.User;
import com.elvo.identity.exception.ApiResponse;
import com.elvo.identity.repository.AuditRepository;
import com.elvo.identity.repository.DeviceRepository;
import com.elvo.identity.repository.SessionRepository;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.service.ProfileManagementService;
import com.elvo.identity.service.SessionManagementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me")
@Validated
public class UserController {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final SessionRepository sessionRepository;
    private final AuditRepository auditRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ProfileManagementService profileManagementService;
    private final SessionManagementService sessionManagementService;

    public UserController(UserRepository userRepository,
                          DeviceRepository deviceRepository,
                          SessionRepository sessionRepository,
                          AuditRepository auditRepository,
                          AuditEventPublisher auditEventPublisher,
                          ProfileManagementService profileManagementService,
                          SessionManagementService sessionManagementService) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.auditRepository = auditRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.profileManagementService = profileManagementService;
        this.sessionManagementService = sessionManagementService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@RequestHeader("X-User-Id") UUID userId) {
        ProfileResponse response = loadProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok("Profile loaded", response));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(@RequestHeader("X-User-Id") UUID userId,
                                                                       @Valid @RequestBody ProfileUpdateRequest request) {
        request.setUserId(userId);
        ProfileResponse response = profileManagementService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", response));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfoResponse>>> getSessions(@RequestHeader("X-User-Id") UUID userId) {
        List<SessionInfoResponse> sessions = sessionManagementService.getActiveSessions(userId);
        return ResponseEntity.ok(ApiResponse.ok("Active sessions loaded", sessions));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@RequestHeader("X-User-Id") UUID userId,
                                                            @PathVariable UUID sessionId) {
        sessionRepository.findById(sessionId)
                .filter(session -> session.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        sessionManagementService.revokeSession(sessionId);
        return ResponseEntity.ok(ApiResponse.ok("Session revoked", null));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions(@RequestHeader("X-User-Id") UUID userId) {
        sessionManagementService.revokeAllUserSessions(userId);
        return ResponseEntity.ok(ApiResponse.ok("All sessions revoked", null));
    }

    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<List<DeviceInfoResponse>>> getDevices(@RequestHeader("X-User-Id") UUID userId) {
        List<DeviceInfoResponse> devices = deviceRepository.findByUserIdOrderByLastUsedAtDesc(userId)
                .stream()
                .map(this::toDeviceResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Devices loaded", devices));
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> revokeDevice(@RequestHeader("X-User-Id") UUID userId,
                                                           @PathVariable UUID deviceId,
                                                           @RequestHeader(value = "X-Source-Ip", required = false) String sourceIp,
                                                           @RequestHeader(value = "X-Source-User-Agent", required = false) String sourceUserAgent) {
        Device device = deviceRepository.findById(deviceId)
                .filter(existing -> existing.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));

        device.setRevoked(true);
        device.setTrusted(false);
        deviceRepository.save(device);
        sessionRepository.findByDeviceIdAndActiveTrueAndRevokedFalse(deviceId)
                .forEach(session -> sessionManagementService.revokeSession(session.getId()));

        Audit audit = new Audit();
        audit.setActionType(Audit.ActionType.DEVICE_REMOVAL);
        audit.setDescription("Device revoked by user");
        audit.setSourceType(Audit.SourceType.USER);
        audit.setSourceIp(sourceIp);
        audit.setSourceUserAgent(sourceUserAgent);
        audit.setDeviceId(deviceId);
        audit.setUser(device.getUser());
        Audit savedAudit = auditRepository.save(audit);
        auditEventPublisher.publish(savedAudit);

        return ResponseEntity.ok(ApiResponse.ok("Device revoked", null));
    }

    private ProfileResponse loadProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new ProfileResponse(
                user.getId(),
                user.getEan(),
                user.getEmail(),
                user.getPhone(),
                user.getDisplayName(),
                user.isMfaEnabled(),
                user.isEspEnabled());
    }

    private DeviceInfoResponse toDeviceResponse(Device device) {
        return new DeviceInfoResponse(
                device.getId(),
                device.getDeviceId(),
                device.getDeviceType(),
                device.isTrusted(),
                device.isRevoked(),
                device.isSuspicious(),
                device.getLastUsedAt());
    }
}
