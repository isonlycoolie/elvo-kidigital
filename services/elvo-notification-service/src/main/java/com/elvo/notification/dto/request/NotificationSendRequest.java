package com.elvo.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NotificationSendRequest {

    @NotBlank
    @Size(max = 64)
    private String channel;

    @NotBlank
    @Size(max = 256)
    private String recipient;

    @NotBlank
    @Size(max = 2000)
    private String message;

    @Size(max = 64)
    private String templateCode;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }
}
