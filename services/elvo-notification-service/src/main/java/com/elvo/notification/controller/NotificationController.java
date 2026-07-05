package com.elvo.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.notification.dto.request.NotificationSendRequest;
import com.elvo.notification.exception.ApiResponse;
import com.elvo.notification.service.NotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<String>> send(@Valid @RequestBody NotificationSendRequest request) {
        String notificationId = notificationService.send(request);
        return ResponseEntity.ok(ApiResponse.ok("Notification accepted", notificationId));
    }
}
