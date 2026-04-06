package com.elvo.identity.client;

public interface SmsProvider {

    void sendSms(String destinationPhone, String message, String requestId);
}
