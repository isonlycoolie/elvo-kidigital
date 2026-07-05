package com.elvo.notification.service.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.elvo.notification.dto.request.NotificationSendRequest;
import com.elvo.notification.service.NotificationService;

@Service
public class DefaultNotificationService implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNotificationService.class);

    @Override
    public String send(NotificationSendRequest request) {
        String notificationId = UUID.randomUUID().toString();
        LOGGER.info("notification_send channel={} recipient={} template={} notificationId={}",
                request.getChannel(),
                maskRecipient(request.getChannel(), request.getRecipient()),
                request.getTemplateCode(),
                notificationId);
        // MVP: log-only delivery; Phase 1.3 wires SMS/email providers and RabbitMQ consumers.
        return notificationId;
    }

    private static String maskRecipient(String channel, String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        return channel + ":" + recipient.substring(0, 2) + "***";
    }
}
