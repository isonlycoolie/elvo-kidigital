package com.elvo.accountmanagement.messaging.publisher;

import com.elvo.accountmanagement.entity.AccountAuditLog;

public interface AccountAuditEventPublisher {

    void publish(AccountAuditLog auditLog);
}
