package com.elvo.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.elvo.notification.dto.request.NotificationSendRequest;

class DefaultNotificationServiceTest {

    @Test
    void sendShouldReturnNotificationId() {
        DefaultNotificationService service = new DefaultNotificationService();
        NotificationSendRequest request = new NotificationSendRequest();
        request.setChannel("SMS");
        request.setRecipient("+255700000000");
        request.setMessage("Your ELVO verification code is 123456");
        request.setTemplateCode("OTP_VERIFY");

        String notificationId = service.send(request);

        assertThat(notificationId).isNotBlank();
    }
}
