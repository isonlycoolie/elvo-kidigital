package com.elvo.identity.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "elvo.communication.notification.enabled", havingValue = "true")
public class NotificationSmsProvider implements SmsProvider {

    private final NotificationDeliveryClient notificationDeliveryClient;

    public NotificationSmsProvider(NotificationDeliveryClient notificationDeliveryClient) {
        this.notificationDeliveryClient = notificationDeliveryClient;
    }

    @Override
    public void sendSms(String destinationPhone, String message, String requestId) {
        notificationDeliveryClient.send("SMS", destinationPhone, message, "OTP_VERIFY");
    }
}
