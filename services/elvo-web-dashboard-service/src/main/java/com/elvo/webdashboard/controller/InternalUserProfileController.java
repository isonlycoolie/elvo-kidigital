package com.elvo.webdashboard.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.webdashboard.dto.request.UserProfileUpdateRequest;
import com.elvo.webdashboard.dto.response.UserProfileResponse;
import com.elvo.webdashboard.exception.ApiResponse;
import com.elvo.webdashboard.service.UserProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/internal/users")
@Validated
public class InternalUserProfileController {

    private final UserProfileService userProfileService;

    public InternalUserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok("Profile loaded", userProfileService.getProfile(userId)));
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userProfileService.upsertProfile(userId, request)));
    }
}
