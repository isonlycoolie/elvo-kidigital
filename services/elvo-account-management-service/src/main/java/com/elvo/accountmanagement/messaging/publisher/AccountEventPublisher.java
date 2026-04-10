package com.elvo.accountmanagement.messaging.publisher;

import com.elvo.accountmanagement.entity.Account;

public interface AccountEventPublisher {

    void publishLifecycle(Account account,
                          String eventType,
                          String reason,
                          String requestId,
                          String correlationId,
                          String sourceService,
                          String sourceIp,
                          String sourceUserAgent,
                          String actor);

    void publishPolicy(Account account,
                       String eventType,
                       String reason,
                       String requestId,
                       String correlationId,
                       String sourceService,
                       String sourceIp,
                       String sourceUserAgent,
                       String actor);
}
