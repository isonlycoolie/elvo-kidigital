package com.elvo.identity.service;

import com.elvo.identity.entity.User;

public interface PostVerificationProvisioningService {

    void provisionIfNeeded(User user);
}
