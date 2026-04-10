package com.elvo.identity.messaging.account;

import com.elvo.identity.entity.User;

public interface AccountCreationIntentPublisher {

    void publish(User user, String sourceIp, String sourceUserAgent);
}
