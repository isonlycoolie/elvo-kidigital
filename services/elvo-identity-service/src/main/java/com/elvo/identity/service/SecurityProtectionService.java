package com.elvo.identity.service;

import com.elvo.identity.entity.User;

public interface SecurityProtectionService {

    void enforcePerUserRateLimit(User user);

    void ensureAccountNotLocked(User user);

    void recordFailedAuthentication(User user, String sourceIp, String sourceUserAgent, String deviceId);

    void recordSuccessfulAuthentication(User user, String sourceIp, String sourceUserAgent, String deviceId);
}
