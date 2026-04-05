package com.elvo.identity.audit;

import com.elvo.identity.entity.Audit;

public interface AuditEventPublisher {

    void publish(Audit audit);
}
