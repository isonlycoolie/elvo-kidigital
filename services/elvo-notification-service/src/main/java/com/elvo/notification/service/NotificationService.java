package com.elvo.notification.service;

import com.elvo.notification.dto.request.NotificationSendRequest;

public interface NotificationService {

    String send(NotificationSendRequest request);
}
